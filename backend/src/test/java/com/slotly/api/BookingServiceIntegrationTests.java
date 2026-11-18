package com.slotly.api;

import com.slotly.api.module.booking.dto.request.AppointmentRequest;
import com.slotly.api.module.booking.entity.Appointment;
import com.slotly.api.module.booking.entity.AppointmentStatus;
import com.slotly.api.module.booking.repository.AppointmentRepository;
import com.slotly.api.module.booking.service.BookingService;
import com.slotly.api.module.booking.service.AvailabilityService;
import com.slotly.api.module.waitlist.service.WaitlistService;
import com.slotly.api.module.place.service.PlaceService;
import com.slotly.api.module.place.dto.request.CalendarExceptionRequest;
import com.slotly.api.module.place.dto.request.OfferingRequest;
import com.slotly.api.module.place.dto.request.PlaceRequest;
import com.slotly.api.module.place.entity.BusinessHour;
import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.OfferingKind;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.place.repository.BusinessHourRepository;
import com.slotly.api.module.place.repository.OfferingRepository;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.place.service.BusinessCalendarService;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.entity.UserRole;
import com.slotly.api.module.user.repository.UserRepository;
import com.slotly.api.module.waitlist.dto.request.WaitlistRequest;
import com.slotly.api.module.waitlist.dto.response.WaitlistResponse;
import com.slotly.api.module.waitlist.entity.WaitlistEntry;
import com.slotly.api.module.waitlist.entity.WaitlistStatus;
import com.slotly.api.module.waitlist.repository.WaitlistRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("integration-test")
@Import(BookingServiceIntegrationTests.TestClockConfiguration.class)
class BookingServiceIntegrationTests {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Istanbul");

    @MockitoBean
    private TaskScheduler scheduler;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private PlaceService placeService;

    @Autowired
    private BusinessCalendarService calendar;

    @Autowired
    private UserRepository users;

    @Autowired
    private PlaceRepository places;

    @Autowired
    private OfferingRepository offerings;

    @Autowired
    private BusinessHourRepository businessHours;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private WaitlistRepository waitlist;

    @Autowired
    private TestClock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User owner;
    private User customerOne;
    private User customerTwo;
    private Place place;
    private LocalDate bookingDate;

    @BeforeEach
    void setUp() {
        clock.setInstant(Instant.now());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = users.save(new User("owner-" + suffix, "hash", "Test", "Owner", UserRole.OWNER));
        customerOne = users.save(new User("customer-a-" + suffix, "hash", "Test", "One", UserRole.CUSTOMER));
        customerTwo = users.save(new User("customer-b-" + suffix, "hash", "Test", "Two", UserRole.CUSTOMER));
        place = places.save(new Place(owner, "Test Place", null, "Test Address", 1));

        bookingDate = LocalDate.now(clock.withZone(BUSINESS_ZONE)).plusDays(2);
        businessHours.save(new BusinessHour(place, bookingDate.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(18, 0)));
    }

    @Test
    void outcomesRequireOwnershipAndEndTimeAndCannotBeChanged() {
        UUID id = book(customerOne, saveService(30), at(10, 0));
        assertStatus(HttpStatus.FORBIDDEN, () ->
            bookingService.recordOutcome(customerOne.getId(), id, AppointmentStatus.COMPLETED));
        assertStatus(HttpStatus.CONFLICT, () ->
            bookingService.recordOutcome(owner.getId(), id, AppointmentStatus.COMPLETED));
        clock.setInstant(at(10, 30));
        assertEquals("COMPLETED",
            bookingService.recordOutcome(owner.getId(), id, AppointmentStatus.COMPLETED).status());
        assertEquals("COMPLETED",
            bookingService.recordOutcome(owner.getId(), id, AppointmentStatus.COMPLETED).status());
        assertStatus(HttpStatus.CONFLICT, () ->
            bookingService.recordOutcome(owner.getId(), id, AppointmentStatus.NO_SHOW));
        assertStatus(HttpStatus.CONFLICT, () -> bookingService.cancel(owner.getId(), id));
        assertEquals(AppointmentStatus.COMPLETED, appointments.findById(id).orElseThrow().getStatus());
    }

    @Test
    void noShowIsPersistedAndCancelledAppointmentsRejectOutcomes() {
        Offering service = saveService(30);
        UUID cancelled = book(customerOne, service, at(10, 0));
        UUID absent = book(customerTwo, service, at(11, 0));
        bookingService.cancel(customerOne.getId(), cancelled);
        clock.setInstant(at(12, 0));
        assertStatus(HttpStatus.CONFLICT, () ->
            bookingService.recordOutcome(owner.getId(), cancelled, AppointmentStatus.COMPLETED));
        assertStatus(HttpStatus.BAD_REQUEST, () ->
            bookingService.recordOutcome(owner.getId(), absent, AppointmentStatus.CANCELLED));
        bookingService.recordOutcome(owner.getId(), absent, AppointmentStatus.NO_SHOW);
        bookingService.recordOutcome(owner.getId(), absent, AppointmentStatus.NO_SHOW);
        assertEquals(AppointmentStatus.NO_SHOW, appointments.findById(absent).orElseThrow().getStatus());
        assertStatus(HttpStatus.CONFLICT, () -> bookingService.cancel(customerTwo.getId(), absent));
    }

    @Test
    void simultaneousRequestsCannotExceedCapacity() throws Exception {
        Offering service = saveService(30);
        Instant startsAt = at(10, 0);
        CyclicBarrier barrier = new CyclicBarrier(2);

        List<Boolean> results = runConcurrently(
            bookAfterBarrier(barrier, customerOne, service, startsAt),
            bookAfterBarrier(barrier, customerTwo, service, startsAt)
        );

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, appointmentsForThisPlace());
    }

    @Test
    void bookingCanEndAtClosingTimeButCannotContinuePastIt() {
        Offering service = saveService(30);
        Instant finalValidSlot = at(17, 30);
        Instant startsTooLate = at(18, 0);

        book(customerOne, service, finalValidSlot);
        assertStatus(HttpStatus.BAD_REQUEST, () -> book(customerTwo, service, startsTooLate));
        assertEquals(1, appointmentsForThisPlace());
    }

    @Test
    void anotherCustomerCannotCancelAnAppointmentAndRepeatedCancellationConflicts() {
        Offering service = saveService(30);
        Instant startsAt = at(11, 0);
        UUID appointmentId = book(customerOne, service, startsAt);

        assertStatus(HttpStatus.FORBIDDEN, () -> bookingService.cancel(customerTwo.getId(), appointmentId));
        assertEquals(AppointmentStatus.CONFIRMED, appointments.findById(appointmentId).orElseThrow().getStatus());

        bookingService.cancel(customerOne.getId(), appointmentId);
        assertStatus(HttpStatus.CONFLICT, () -> bookingService.cancel(customerOne.getId(), appointmentId));
    }

    @Test
    void appointmentMustBeInTheFuture() {
        Offering service = saveService(30);
        Instant startsAt = clock.instant().minusSeconds(60);

        assertStatus(HttpStatus.BAD_REQUEST, () -> book(customerOne, service, startsAt));
        assertEquals(0, appointmentsForThisPlace());
    }

    @Test
    void anotherOwnerCannotChangeAPlace() {
        User anotherOwner = users.save(new User(
            "owner-other-" + UUID.randomUUID().toString().substring(0, 8),
            "hash",
            "Other",
            "Owner",
            UserRole.OWNER
        ));
        PlaceRequest update = new PlaceRequest("Changed", null, "Elsewhere", 1, true);

        assertStatus(HttpStatus.FORBIDDEN, () -> placeService.updatePlace(anotherOwner.getId(), place.getId(), update));
    }

    @Test
    void ownerCanSeeInactiveOfferingsToManageThem() {
        Offering service = saveService(30);
        service.update(service.getName(), null, service.getPrice(), 30, service.getKind(), false);
        offerings.save(service);

        var visibleOfferings = placeService.ownerOfferings(owner.getId(), place.getId());

        assertEquals(1, visibleOfferings.size());
        assertFalse(visibleOfferings.getFirst().active());
    }

    @Test
    void overlappingServiceDurationsShareCapacity() {
        setCapacity(2);
        Offering longService = saveService(60);
        Offering shortService = saveService(30);
        Instant firstStart = at(10, 0);
        Instant overlapStart = at(10, 30);

        book(customerOne, longService, firstStart);
        book(customerTwo, shortService, overlapStart);

        assertEquals(2, appointmentsForThisPlace());
        assertFalse(appointments.findByPlaceIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
            place.getId(),
            bookingDate.atStartOfDay(BUSINESS_ZONE).toInstant(),
            bookingDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()
        ).stream().anyMatch(
            appointment -> appointment.getStatus() == AppointmentStatus.CANCELLED
        ));
    }

    @Test
    void cancellationAndOfferTimeoutMoveTheFifoQueueForward() {
        Offering service = saveService(30);
        Instant startsAt = at(10, 0);
        UUID appointmentId = book(customerOne, service, startsAt);

        var first = joinWaitlist(customerTwo, service, startsAt);
        clock.advance(Duration.ofMillis(5));
        User thirdCustomer = users.save(new User(
            "customer-c-" + UUID.randomUUID().toString().substring(0, 8),
            "hash",
            "Test",
            "Three",
            UserRole.CUSTOMER
        ));
        var second = joinWaitlist(thirdCustomer, service, startsAt);

        bookingService.cancel(customerOne.getId(), appointmentId);
        assertEquals("OFFERED", waitlist.findById(first.id()).orElseThrow().getStatus().name());
        assertEquals("WAITING", waitlist.findById(second.id()).orElseThrow().getStatus().name());

        clock.advance(Duration.ofMinutes(31));
        waitlistService.expireOffers();
        assertEquals("EXPIRED", waitlist.findById(first.id()).orElseThrow().getStatus().name());
        assertEquals("OFFERED", waitlist.findById(second.id()).orElseThrow().getStatus().name());

        waitlistService.acceptWaitlistOffer(thirdCustomer.getId(), second.id());
        assertEquals("ACCEPTED", waitlist.findById(second.id()).orElseThrow().getStatus().name());
        assertEquals(2, appointmentsForThisPlace());
    }

    @Test
    void consecutiveAppointmentsDoNotConsumeTwoSeatsAtTheSameTime() {
        setCapacity(2);
        Offering shortService = saveService(30);
        Offering longService = saveService(60);
        Instant start = at(10, 0);
        book(customerOne, shortService, start);
        book(customerTwo, shortService, start.plusSeconds(1800));

        book(customerOne, longService, start);

        assertEquals(3, appointmentsForThisPlace());
    }

    @Test
    void repeatedBookingCannotCreateADuplicateWhenCapacityRemains() {
        setCapacity(2);
        Offering service = saveService(30);
        Instant start = at(10, 0);
        AppointmentRequest request = new AppointmentRequest(place.getId(), service.getId(), start);
        bookingService.createAppointment(customerOne.getId(), request);

        assertStatus(HttpStatus.CONFLICT, () -> bookingService.createAppointment(customerOne.getId(), request));
        assertEquals(1, appointmentsForThisPlace());
    }

    @Test
    void concurrentAcceptanceCreatesOnlyOneAppointmentWithMultipleFreeSeats() throws Exception {
        setCapacity(3);
        Offering service = saveService(30);
        Instant start = at(10, 0);
        User third = users.save(new User(
            "third-" + UUID.randomUUID().toString().substring(0, 8),
            "hash", "Third", "Test", UserRole.CUSTOMER
        ));
        User waiting = users.save(new User(
            "waiting-" + UUID.randomUUID().toString().substring(0, 8),
            "hash", "Waiting", "Test", UserRole.CUSTOMER
        ));
        UUID first = book(customerOne, service, start);
        UUID second = book(customerTwo, service, start);
        book(third, service, start);
        UUID entryId = joinWaitlist(waiting, service, start).id();
        bookingService.cancel(customerOne.getId(), first);
        bookingService.cancel(customerTwo.getId(), second);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Boolean> accept = () -> new TransactionTemplate(transactionManager)
            .execute(status -> {
                waitlist.findById(entryId).orElseThrow();
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    waitlistService.acceptWaitlistOffer(waiting.getId(), entryId);
                    return true;
                } catch (ResponseStatusException exception) {
                    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                    return false;
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
        List<Boolean> results = runConcurrently(accept, accept);
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals("ACCEPTED", waitlist.findById(entryId).orElseThrow().getStatus().name());
        assertEquals(1, bookingService.myAppointments(waiting.getId()).size());
    }

    @Test
    void activeOfferBlocksDirectBookingsAndOverlappingServicesButCanBeAccepted() {
        Offering service = saveService(60);
        Offering shortService = saveService(30);
        Instant start = at(10, 0);
        UUID appointment = book(customerOne, service, start);
        UUID entry = joinWaitlist(customerTwo, service, start).id();
        bookingService.cancel(customerOne.getId(), appointment);

        assertFalse(availabilityService.availability(place.getId(), service.getId(), start, start.plusSeconds(3600)).contains(start));
        assertStatus(HttpStatus.CONFLICT, () -> book(customerOne, shortService, start.plusSeconds(1800)));
        waitlistService.acceptWaitlistOffer(customerTwo.getId(), entry);
        assertEquals(1, bookingService.myAppointments(customerTwo.getId()).size());
    }

    @ParameterizedTest(name = "decline={0}")
    @ValueSource(booleans = {true, false})
    void decliningOrLeavingAnOfferReleasesItsHold(boolean decline) {
        Offering service = saveService(30);
        Instant start = at(10, 0);
        UUID appointment = book(customerOne, service, start);
        UUID entry = joinWaitlist(customerTwo, service, start).id();
        bookingService.cancel(customerOne.getId(), appointment);
        if (decline) {
            waitlistService.declineWaitlistOffer(customerTwo.getId(), entry);
        } else {
            waitlistService.leaveWaitlist(customerTwo.getId(), entry);
        }
        assertEquals(List.of(start), availabilityService.availability(
            place.getId(), service.getId(), start, start.plusSeconds(1800)
        ));
    }

    @Test
    void expirationAtTheDeadlinePassesTheHoldToTheNextCustomerBeforeDirectBooking() {
        Offering service = saveService(30);
        Instant start = at(10, 0);
        UUID appointment = book(customerOne, service, start);
        UUID first = joinWaitlist(customerTwo, service, start).id();
        clock.advance(Duration.ofMillis(5));
        UUID second = joinWaitlist(customerOne, service, start).id();
        bookingService.cancel(customerOne.getId(), appointment);
        clock.setInstant(waitlist.findById(first).orElseThrow().getOfferExpiresAt());

        assertStatus(HttpStatus.CONFLICT, () -> book(customerTwo, service, start));
        assertEquals("EXPIRED", waitlist.findById(first).orElseThrow().getStatus().name());
        assertEquals("OFFERED", waitlist.findById(second).orElseThrow().getStatus().name());
        waitlistService.acceptWaitlistOffer(customerOne.getId(), second);
    }

    @Test
    void cancellationAfterConversionToProductUsesTheStoredWindow() {
        Offering longService = saveService(60);
        Offering shortService = saveService(30);
        UUID booked = book(customerOne, longService, at(10, 0));
        UUID converted = joinWaitlist(customerTwo, longService, at(10, 0)).id();
        UUID overlapping = joinWaitlist(customerTwo, shortService, at(10, 30)).id();
        placeService.updateOffering(owner.getId(), place.getId(), longService.getId(),
            new OfferingRequest("Product", null, BigDecimal.TEN, null, OfferingKind.PRODUCT, true));

        bookingService.cancel(customerOne.getId(), booked);

        assertEquals(AppointmentStatus.CANCELLED, appointments.findById(booked).orElseThrow().getStatus());
        assertEquals(WaitlistStatus.WAITING, waitlist.findById(converted).orElseThrow().getStatus());
        assertEquals(WaitlistStatus.OFFERED, waitlist.findById(overlapping).orElseThrow().getStatus());
    }

    @Test
    void capacityIncreaseReservesNewSeatsForWaitingCustomers() {
        Offering service = saveService(30);
        Instant start = at(10, 0);
        book(customerOne, service, start);
        UUID first = joinWaitlist(customerTwo, service, start).id();
        UUID second = joinWaitlist(owner, service, start).id();

        changeCapacity(2);
        assertEquals(WaitlistStatus.OFFERED, waitlist.findById(first).orElseThrow().getStatus());
        assertEquals(WaitlistStatus.WAITING, waitlist.findById(second).orElseThrow().getStatus());
        changeCapacity(3);
        assertEquals(WaitlistStatus.OFFERED, waitlist.findById(second).orElseThrow().getStatus());
    }

    @Test
    void twoExpiredOffersChooseBetweenOverlappingWaitersInOriginalOrder() {
        Offering hourly = saveService(60);
        Offering halfHour = saveService(30);
        Offering fortyFive = saveService(45);
        for (int hour : List.of(9, 10)) {
            var expired = new WaitlistEntry(owner, place, hourly, at(hour, 0), at(hour + 1, 0));
            expired.offer(clock.instant().minusSeconds(1801), clock.instant().minusSeconds(1));
            waitlist.save(expired);
        }
        var first = waitlist.save(new WaitlistEntry(customerOne, place, halfHour, at(10, 0), at(10, 30)));
        var second = waitlist.save(new WaitlistEntry(customerTwo, place, fortyFive, at(9, 45), at(10, 30)));

        waitlistService.expireOffers();

        assertEquals(WaitlistStatus.OFFERED, waitlist.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(WaitlistStatus.WAITING, waitlist.findById(second.getId()).orElseThrow().getStatus());
    }

    @Test
    void offerEndsAtAppointmentStartAndPastWaitingEntriesClose() {
        Offering service = saveService(30);
        Instant start = at(10, 0);
        UUID booked = book(customerOne, service, start);
        UUID first = joinWaitlist(customerTwo, service, start).id();
        UUID second = joinWaitlist(owner, service, start).id();
        clock.setInstant(start.minusSeconds(300));
        bookingService.cancel(customerOne.getId(), booked);
        assertEquals(start, waitlist.findById(first).orElseThrow().getOfferExpiresAt());

        clock.setInstant(start);
        assertEquals("EXPIRED", waitlistService.myWaitlist(owner.getId()).getFirst().status());
        assertStatus(HttpStatus.GONE, () -> waitlistService.acceptWaitlistOffer(customerTwo.getId(), first));
        waitlistService.expireOffers();
        assertEquals(WaitlistStatus.EXPIRED, waitlist.findById(first).orElseThrow().getStatus());
        assertEquals(WaitlistStatus.EXPIRED, waitlist.findById(second).orElseThrow().getStatus());
        assertEquals(1, appointmentsForThisPlace());
    }

    @ParameterizedTest
    @ValueSource(strings = {"place", "offering", "duration", "duplicate"})
    void staleOfferCannotCreateAnInvalidAppointment(String change) {
        Offering service = saveService(30);
        Instant start = at(10, 0);
        UUID booked = book(customerOne, service, start);
        UUID entry = joinWaitlist(customerTwo, service, start).id();
        bookingService.cancel(customerOne.getId(), booked);
        switch (change) {
            case "place" -> placeService.deletePlace(owner.getId(), place.getId());
            case "offering" -> placeService.deleteOffering(owner.getId(), place.getId(), service.getId());
            case "duration" -> placeService.updateOffering(owner.getId(), place.getId(), service.getId(),
                new OfferingRequest("Service", null, BigDecimal.TEN, 60, OfferingKind.SERVICE, true));
            case "duplicate" -> {
                changeCapacity(2);
                book(customerTwo, service, start);
            }
        }

        assertStatus(HttpStatus.CONFLICT, () -> waitlistService.acceptWaitlistOffer(customerTwo.getId(), entry));
        assertEquals(WaitlistStatus.EXPIRED, waitlist.findById(entry).orElseThrow().getStatus());
        assertEquals(change.equals("duplicate") ? 1 : 0, bookingService.myAppointments(customerTwo.getId()).size());
    }

    @Test
    void databaseRejectsDuplicateConfirmedRowsButAllowsRebookingAfterCancellation() {
        Offering service = saveService(30);
        Instant start = at(10, 0);
        UUID first = book(customerOne, service, start);
        assertThrows(DataIntegrityViolationException.class, () -> appointments.saveAndFlush(
            new Appointment(customerOne, place, service, start, start.plusSeconds(1800))));
        bookingService.cancel(customerOne.getId(), first);
        book(customerOne, service, start);
        assertEquals(2, appointmentsForThisPlace());
    }

    @Test
    void publishedStartIsAcceptedAndSubMinuteOffsetsAreRejectedForBothPaths() {
        Offering service = saveService(30);
        Instant start = at(9, 0);
        assertEquals(List.of(start), availabilityService.availability(place.getId(), service.getId(),
            start, start.plusSeconds(1800)));
        book(customerOne, service, start);
        assertStatus(HttpStatus.BAD_REQUEST, () -> book(customerTwo, service, start.plusSeconds(25)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> joinWaitlist(customerTwo, service, start.plusNanos(1)));
    }

    @Test
    void inactivePlaceAndOfferingStartHiddenFromDiscovery() {
        var hidden = placeService.createPlace(owner.getId(), new PlaceRequest("Hidden", null, "Address", 1, false));
        assertFalse(hidden.active());
        assertFalse(placeService.publicPlaces().stream().anyMatch(item -> item.id().equals(hidden.id())));
        assertStatus(HttpStatus.NOT_FOUND, () -> placeService.publicPlace(hidden.id()));
        var offering = placeService.createOffering(owner.getId(), place.getId(),
            new OfferingRequest("Hidden", null, BigDecimal.TEN, 30, OfferingKind.SERVICE, false));
        assertFalse(offering.active());
        assertTrue(placeService.publicOfferings(place.getId()).isEmpty());
        assertEquals(BUSINESS_ZONE.getId(), placeService.publicPlace(place.getId()).timeZone());
    }

    @Test
    void directBookingCannotWinAgainstAcceptanceOfAHeldSeat() throws Exception {
        Offering service = saveService(30);
        Instant start = at(10, 0);
        UUID appointment = book(customerOne, service, start);
        UUID entry = joinWaitlist(customerTwo, service, start).id();
        bookingService.cancel(customerOne.getId(), appointment);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Boolean> accept = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            waitlistService.acceptWaitlistOffer(customerTwo.getId(), entry);
            return true;
        };
        List<Boolean> results = runConcurrently(bookAfterBarrier(barrier, customerOne, service, start), accept);
        assertFalse(results.getFirst());
        assertTrue(results.getLast());
        assertEquals(1, bookingService.myAppointments(customerTwo.getId()).size());
    }

    @Test
    void aHoldReservesOnlyOneSeatWhenOtherCapacityBecomesAvailable() {
        setCapacity(2);
        Offering service = saveService(30);
        Instant start = at(10, 0);
        UUID first = book(customerOne, service, start);
        UUID second = book(customerTwo, service, start);
        UUID entry = joinWaitlist(owner, service, start).id();
        bookingService.cancel(customerOne.getId(), first);
        bookingService.cancel(customerTwo.getId(), second);
        book(customerOne, service, start);
        waitlistService.acceptWaitlistOffer(owner.getId(), entry);
        assertFalse(availabilityService.availability(place.getId(), service.getId(), start, start.plusSeconds(1800)).contains(start));
    }

    @Test
    void closedDateBlocksAvailabilityBookingAndWaitlist() {
        Offering service = saveService(30);
        Instant start = at(10, 0);
        closeDate();
        assertEquals(List.of(), availabilityService.availability(place.getId(), service.getId(), start, start.plusSeconds(3600)));
        assertStatus(HttpStatus.BAD_REQUEST, () -> book(customerOne, service, start));
        assertStatus(HttpStatus.BAD_REQUEST, () -> joinWaitlist(customerOne, service, start));
        calendar.delete(owner.getId(), place.getId(), bookingDate);
        book(customerOne, service, start);
    }

    @Test
    void specialHoursReplaceWeeklyHoursAndAnchorServiceSlots() {
        Offering service = saveService(30);
        calendar.save(owner.getId(), place.getId(), bookingDate,
            new CalendarExceptionRequest(false, LocalTime.of(12, 15), LocalTime.of(13, 15)));
        Instant first = at(12, 15);
        assertEquals(List.of(first, first.plusSeconds(1800)), availabilityService.availability(
            place.getId(), service.getId(), bookingDate.atStartOfDay(BUSINESS_ZONE).toInstant(),
            bookingDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()
        ));
        book(customerOne, service, first);
        assertStatus(HttpStatus.BAD_REQUEST, () -> book(customerTwo, service, first.plusSeconds(2700)));
    }

    @Test
    void calendarChangesCannotInvalidateAppointmentsAndRollbackTheException() {
        Offering service = saveService(30);
        Instant start = at(10, 0);
        UUID appointment = book(customerOne, service, start);
        assertStatus(HttpStatus.CONFLICT, this::closeDate);
        assertEquals(0, calendar.list(owner.getId(), place.getId()).size());
        var hour = businessHours.findByPlaceIdOrderByDayOfWeekAscOpensAtAsc(place.getId()).getFirst();
        assertThrows(ResponseStatusException.class,
            () -> placeService.deleteHour(owner.getId(), place.getId(), hour.getId()));
        assertEquals(1, placeService.getHours(place.getId()).size());
        bookingService.cancel(customerOne.getId(), appointment);
        closeDate();
    }

    @Test
    void activeOffersAlsoPreventClosingTheirDate() {
        Offering service = saveService(30);
        Instant start = at(10, 0);
        UUID appointment = book(customerOne, service, start);
        UUID entry = joinWaitlist(customerTwo, service, start).id();
        bookingService.cancel(customerOne.getId(), appointment);
        assertStatus(HttpStatus.CONFLICT, this::closeDate);
        waitlistService.declineWaitlistOffer(customerTwo.getId(), entry);
        closeDate();
    }

    @Test
    void removingSpecialHoursCannotStrandAnExistingAppointment() {
        Offering service = saveService(30);
        calendar.save(owner.getId(), place.getId(), bookingDate,
            new CalendarExceptionRequest(false, LocalTime.of(19, 0), LocalTime.of(20, 0)));
        Instant start = at(19, 0);
        book(customerOne, service, start);
        assertStatus(HttpStatus.CONFLICT, () -> calendar.delete(owner.getId(), place.getId(), bookingDate));
        assertEquals(1, calendar.list(owner.getId(), place.getId()).size());
    }

    @Test
    void calendarRejectsInvalidHoursAndOtherOwners() {
        assertThrows(ResponseStatusException.class,
            () -> calendar.save(owner.getId(), place.getId(), bookingDate,
                new CalendarExceptionRequest(false, LocalTime.NOON, LocalTime.of(9, 0))));
        assertStatus(HttpStatus.FORBIDDEN, () -> calendar.save(customerOne.getId(), place.getId(), bookingDate,
                new CalendarExceptionRequest(true, null, null)));
        assertEquals(0, calendar.list(owner.getId(), place.getId()).size());
    }

    private List<Boolean> runConcurrently(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results = executor.invokeAll(List.of(first, second));
            return List.of(results.getFirst().get(), results.getLast().get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void capacityReductionRejectsOverlappingAppointmentsAndRollsBackOtherChanges() {
        setCapacity(3);
        Offering service = saveService(30);
        book(customerOne, service, at(10, 0));
        book(customerTwo, service, at(10, 0));

        assertStatus(HttpStatus.CONFLICT, () -> changeCapacity(1));
        Place unchanged = places.findById(place.getId()).orElseThrow();
        assertEquals(3, unchanged.getCapacity());
        assertEquals("Test Place", unchanged.getName());
        changeCapacity(2);
        assertEquals(2, places.findById(place.getId()).orElseThrow().getCapacity());
    }

    @Test
    void capacityReductionCountsActiveOffersButNotExpiredOffers() {
        setCapacity(2);
        Offering service = saveService(30);
        UUID first = book(customerOne, service, at(10, 0));
        book(customerTwo, service, at(10, 0));
        joinWaitlist(customerOne, service, at(10, 0));
        bookingService.cancel(customerOne.getId(), first);

        assertStatus(HttpStatus.CONFLICT, () -> changeCapacity(1));
        clock.advance(Duration.ofMinutes(30));
        changeCapacity(1);
        assertEquals(1, places.findById(place.getId()).orElseThrow().getCapacity());
    }

    @Test
    void capacityReductionUsesPeakOccupancyRatherThanTotalAppointments() {
        setCapacity(3);
        Offering service = saveService(30);
        book(customerOne, service, at(10, 0));
        book(customerTwo, service, at(10, 30));

        changeCapacity(1);
        assertEquals(1, places.findById(place.getId()).orElseThrow().getCapacity());
    }

    @Test
    void capacityReductionAndBookingCannotTogetherExceedCapacity() throws Exception {
        setCapacity(2);
        Offering service = saveService(30);
        book(customerOne, service, at(10, 0));
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Boolean> results = runConcurrently(
            () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    changeCapacity(1);
                    return true;
                } catch (ResponseStatusException exception) {
                    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                    return false;
                }
            },
            bookAfterBarrier(barrier, customerTwo, service, at(10, 0))
        );

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertTrue(appointmentsForThisPlace() <= places.findById(place.getId()).orElseThrow().getCapacity());
    }

    private void changeCapacity(int capacity) {
        placeService.updatePlace(owner.getId(), place.getId(), new PlaceRequest(
            "Changed place", null, "Changed address", capacity, true
        ));
    }

    private void closeDate() {
        calendar.save(owner.getId(), place.getId(), bookingDate, new CalendarExceptionRequest(true, null, null));
    }

    private Instant at(int hour, int minute) {
        return bookingDate.atTime(hour, minute).atZone(BUSINESS_ZONE).toInstant();
    }

    private UUID book(User customer, Offering service, Instant start) {
        return bookingService.createAppointment(
            customer.getId(), new AppointmentRequest(place.getId(), service.getId(), start)
        ).id();
    }

    private WaitlistResponse joinWaitlist(User customer, Offering service, Instant start) {
        return waitlistService.joinWaitlist(
            customer.getId(), new WaitlistRequest(place.getId(), service.getId(), start)
        );
    }

    private void setCapacity(int capacity) {
        place.update(place.getName(), place.getDescription(), place.getAddress(), capacity, true);
        places.save(place);
    }

    private void assertStatus(HttpStatus expected, Executable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action);
        assertEquals(expected, error.getStatusCode());
    }

    private Callable<Boolean> bookAfterBarrier(
        CyclicBarrier barrier,
        User customer,
        Offering service,
        Instant startsAt
    ) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                book(customer, service, startsAt);
                return true;
            } catch (ResponseStatusException exception) {
                assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                return false;
            }
        };
    }

    private Offering saveService(int durationMinutes) {
        return offerings.save(new Offering(
            place,
            "Test Service " + durationMinutes,
            null,
            new BigDecimal("25.00"),
            durationMinutes,
            OfferingKind.SERVICE
        ));
    }

    private long appointmentsForThisPlace() {
        return appointments.findByPlaceIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
            place.getId(),
            bookingDate.atStartOfDay(BUSINESS_ZONE).toInstant(),
            bookingDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()
        ).size();
    }

    @TestConfiguration
    static class TestClockConfiguration {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    static class TestClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.now());

        void setInstant(Instant value) {
            instant.set(value);
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
