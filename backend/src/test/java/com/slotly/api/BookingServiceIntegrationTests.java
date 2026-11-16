package com.slotly.api;

import com.slotly.api.module.booking.dto.request.AppointmentRequest;
import com.slotly.api.module.waitlist.dto.request.WaitlistRequest;
import com.slotly.api.module.place.dto.request.PlaceRequest;
import com.slotly.api.module.booking.entity.AppointmentStatus;
import com.slotly.api.module.place.entity.BusinessHour;
import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.OfferingKind;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.entity.UserRole;
import com.slotly.api.module.booking.repository.AppointmentRepository;
import com.slotly.api.module.place.repository.BusinessHourRepository;
import com.slotly.api.module.place.repository.OfferingRepository;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.user.repository.UserRepository;
import com.slotly.api.module.waitlist.repository.WaitlistRepository;
import com.slotly.api.module.booking.service.BookingService;
import com.slotly.api.module.place.service.BusinessCalendarService;
import com.slotly.api.module.place.dto.request.CalendarExceptionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("integration-test")
@Import(BookingServiceIntegrationTests.TestClockConfiguration.class)
class BookingServiceIntegrationTests {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Istanbul");

    @Autowired
    private BookingService bookingService;

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
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

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
    void simultaneousRequestsCannotExceedCapacity() throws Exception {
        Offering service = saveService(30);
        Instant startsAt = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        CyclicBarrier barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);

        try {
            Callable<Boolean> first = bookAfterBarrier(barrier, customerOne, service, startsAt);
            Callable<Boolean> second = bookAfterBarrier(barrier, customerTwo, service, startsAt);
            List<Future<Boolean>> results = executor.invokeAll(List.of(first, second));

            long successes = results.stream().filter(result -> {
                try {
                    return result.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).count();

            assertEquals(1, successes);
            assertEquals(1, appointmentsForThisPlace());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void bookingCanEndAtClosingTimeButCannotContinuePastIt() {
        Offering service = saveService(30);
        Instant finalValidSlot = bookingDate.atTime(17, 30).atZone(BUSINESS_ZONE).toInstant();
        Instant startsTooLate = bookingDate.atTime(18, 0).atZone(BUSINESS_ZONE).toInstant();

        bookingService.createAppointment(customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), finalValidSlot));
        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> bookingService.createAppointment(
                customerTwo.getId(),
                new AppointmentRequest(place.getId(), service.getId(), startsTooLate)
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals(1, appointmentsForThisPlace());
    }

    @Test
    void anotherCustomerCannotCancelAnAppointmentAndRepeatedCancellationConflicts() {
        Offering service = saveService(30);
        Instant startsAt = bookingDate.atTime(11, 0).atZone(BUSINESS_ZONE).toInstant();
        UUID appointmentId = bookingService.createAppointment(
            customerOne.getId(),
            new AppointmentRequest(place.getId(), service.getId(), startsAt)
        ).id();

        ResponseStatusException forbidden = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> bookingService.cancel(customerTwo.getId(), appointmentId)
        );
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        assertEquals(AppointmentStatus.CONFIRMED, appointments.findById(appointmentId).orElseThrow().getStatus());

        bookingService.cancel(customerOne.getId(), appointmentId);
        ResponseStatusException conflict = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> bookingService.cancel(customerOne.getId(), appointmentId)
        );
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }

    @Test
    void appointmentMustBeInTheFuture() {
        Offering service = saveService(30);
        Instant startsAt = clock.instant().minusSeconds(60);

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> bookingService.createAppointment(
                customerOne.getId(),
                new AppointmentRequest(place.getId(), service.getId(), startsAt)
            )
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
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

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> bookingService.updatePlace(anotherOwner.getId(), place.getId(), update)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void ownerCanSeeInactiveOfferingsToManageThem() {
        Offering service = saveService(30);
        service.update(service.getName(), null, service.getPrice(), 30, service.getKind(), false);
        offerings.save(service);

        var visibleOfferings = bookingService.ownerOfferings(owner.getId(), place.getId());

        assertEquals(1, visibleOfferings.size());
        assertFalse(visibleOfferings.getFirst().active());
    }

    @Test
    void overlappingServiceDurationsShareCapacity() {
        place.update(place.getName(), place.getDescription(), place.getAddress(), 2, true);
        places.save(place);
        Offering longService = saveService(60);
        Offering shortService = saveService(30);
        Instant firstStart = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        Instant overlapStart = bookingDate.atTime(10, 30).atZone(BUSINESS_ZONE).toInstant();

        bookingService.createAppointment(customerOne.getId(), new AppointmentRequest(place.getId(), longService.getId(), firstStart));
        bookingService.createAppointment(customerTwo.getId(), new AppointmentRequest(place.getId(), shortService.getId(), overlapStart));

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
        Instant startsAt = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        UUID appointmentId = bookingService.createAppointment(
            customerOne.getId(),
            new AppointmentRequest(place.getId(), service.getId(), startsAt)
        ).id();

        var first = bookingService.joinWaitlist(
            customerTwo.getId(),
            new WaitlistRequest(place.getId(), service.getId(), startsAt)
        );
        clock.advance(Duration.ofMillis(5));
        User thirdCustomer = users.save(new User(
            "customer-c-" + UUID.randomUUID().toString().substring(0, 8),
            "hash",
            "Test",
            "Three",
            UserRole.CUSTOMER
        ));
        var second = bookingService.joinWaitlist(
            thirdCustomer.getId(),
            new WaitlistRequest(place.getId(), service.getId(), startsAt)
        );

        bookingService.cancel(customerOne.getId(), appointmentId);
        assertEquals("OFFERED", waitlist.findById(first.id()).orElseThrow().getStatus().name());
        assertEquals("WAITING", waitlist.findById(second.id()).orElseThrow().getStatus().name());

        clock.advance(Duration.ofMinutes(31));
        bookingService.expireOffers();
        assertEquals("EXPIRED", waitlist.findById(first.id()).orElseThrow().getStatus().name());
        assertEquals("OFFERED", waitlist.findById(second.id()).orElseThrow().getStatus().name());

        bookingService.acceptWaitlistOffer(thirdCustomer.getId(), second.id());
        assertEquals("ACCEPTED", waitlist.findById(second.id()).orElseThrow().getStatus().name());
        assertEquals(2, appointmentsForThisPlace());
    }

    @Test
    void consecutiveAppointmentsDoNotConsumeTwoSeatsAtTheSameTime() {
        place.update(place.getName(), null, place.getAddress(), 2, true);
        places.save(place);
        Offering shortService = saveService(30);
        Offering longService = saveService(60);
        Instant start = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        bookingService.createAppointment(customerOne.getId(), new AppointmentRequest(place.getId(), shortService.getId(), start));
        bookingService.createAppointment(customerTwo.getId(), new AppointmentRequest(place.getId(), shortService.getId(), start.plusSeconds(1800)));

        bookingService.createAppointment(customerOne.getId(), new AppointmentRequest(place.getId(), longService.getId(), start));

        assertEquals(3, appointmentsForThisPlace());
    }

    @Test
    void repeatedBookingCannotCreateADuplicateWhenCapacityRemains() {
        place.update(place.getName(), null, place.getAddress(), 2, true);
        places.save(place);
        Offering service = saveService(30);
        Instant start = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        AppointmentRequest request = new AppointmentRequest(place.getId(), service.getId(), start);
        bookingService.createAppointment(customerOne.getId(), request);

        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> bookingService.createAppointment(customerOne.getId(), request)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(1, appointmentsForThisPlace());
    }

    @Test
    void concurrentAcceptanceCreatesOnlyOneAppointmentWithMultipleFreeSeats() throws Exception {
        place.update(place.getName(), null, place.getAddress(), 3, true);
        places.save(place);
        Offering service = saveService(30);
        Instant start = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        User third = users.save(new User(
            "third-" + UUID.randomUUID().toString().substring(0, 8),
            "hash", "Third", "Test", UserRole.CUSTOMER
        ));
        User waiting = users.save(new User(
            "waiting-" + UUID.randomUUID().toString().substring(0, 8),
            "hash", "Waiting", "Test", UserRole.CUSTOMER
        ));
        UUID first = bookingService.createAppointment(customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), start)).id();
        UUID second = bookingService.createAppointment(customerTwo.getId(), new AppointmentRequest(place.getId(), service.getId(), start)).id();
        bookingService.createAppointment(third.getId(), new AppointmentRequest(place.getId(), service.getId(), start));
        UUID entryId = bookingService.joinWaitlist(waiting.getId(), new WaitlistRequest(place.getId(), service.getId(), start)).id();
        bookingService.cancel(customerOne.getId(), first);
        bookingService.cancel(customerTwo.getId(), second);
        CyclicBarrier barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> accept = () -> new org.springframework.transaction.support.TransactionTemplate(transactionManager)
            .execute(status -> {
                waitlist.findById(entryId).orElseThrow();
                try {
                    barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    bookingService.acceptWaitlistOffer(waiting.getId(), entryId);
                    return true;
                } catch (ResponseStatusException exception) {
                    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                    return false;
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });
        try {
            List<Future<Boolean>> results = executor.invokeAll(List.of(accept, accept));
            int successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successes++;
                }
            }
            assertEquals(1, successes);
            assertEquals("ACCEPTED", waitlist.findById(entryId).orElseThrow().getStatus().name());
            assertEquals(1, bookingService.myAppointments(waiting.getId()).size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void activeOfferBlocksDirectBookingsAndOverlappingServicesButCanBeAccepted() {
        Offering service = saveService(60);
        Offering shortService = saveService(30);
        Instant start = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        UUID appointment = bookingService.createAppointment(
            customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), start)
        ).id();
        UUID entry = bookingService.joinWaitlist(
            customerTwo.getId(), new WaitlistRequest(place.getId(), service.getId(), start)
        ).id();
        bookingService.cancel(customerOne.getId(), appointment);

        assertFalse(bookingService.availability(place.getId(), service.getId(), start, start.plusSeconds(3600)).contains(start));
        ResponseStatusException conflict = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> bookingService.createAppointment(customerOne.getId(),
                new AppointmentRequest(place.getId(), shortService.getId(), start.plusSeconds(1800)))
        );
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        bookingService.acceptWaitlistOffer(customerTwo.getId(), entry);
        assertEquals(1, bookingService.myAppointments(customerTwo.getId()).size());
    }

    @Test
    void decliningOrLeavingAnOfferReleasesItsHold() {
        Offering service = saveService(30);
        Instant start = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        for (boolean decline : List.of(true, false)) {
            UUID appointment = bookingService.createAppointment(
                customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), start)
            ).id();
            UUID entry = bookingService.joinWaitlist(
                customerTwo.getId(), new WaitlistRequest(place.getId(), service.getId(), start)
            ).id();
            bookingService.cancel(customerOne.getId(), appointment);
            if (decline) {
                bookingService.declineWaitlistOffer(customerTwo.getId(), entry);
            } else {
                bookingService.leaveWaitlist(customerTwo.getId(), entry);
            }
            assertEquals(List.of(start), bookingService.availability(
                place.getId(), service.getId(), start, start.plusSeconds(1800)
            ));
        }
    }

    @Test
    void expirationAtTheDeadlinePassesTheHoldToTheNextCustomerBeforeDirectBooking() {
        Offering service = saveService(30);
        Instant start = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        UUID appointment = bookingService.createAppointment(
            customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), start)
        ).id();
        UUID first = bookingService.joinWaitlist(
            customerTwo.getId(), new WaitlistRequest(place.getId(), service.getId(), start)
        ).id();
        clock.advance(Duration.ofMillis(5));
        UUID second = bookingService.joinWaitlist(
            customerOne.getId(), new WaitlistRequest(place.getId(), service.getId(), start)
        ).id();
        bookingService.cancel(customerOne.getId(), appointment);
        clock.setInstant(waitlist.findById(first).orElseThrow().getOfferExpiresAt());

        ResponseStatusException conflict = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> bookingService.createAppointment(customerTwo.getId(),
                new AppointmentRequest(place.getId(), service.getId(), start))
        );
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        bookingService.expireOffers();
        assertEquals("EXPIRED", waitlist.findById(first).orElseThrow().getStatus().name());
        assertEquals("OFFERED", waitlist.findById(second).orElseThrow().getStatus().name());
        bookingService.acceptWaitlistOffer(customerOne.getId(), second);
    }

    @Test
    void directBookingCannotWinAgainstAcceptanceOfAHeldSeat() throws Exception {
        Offering service = saveService(30);
        Instant start = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        UUID appointment = bookingService.createAppointment(
            customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), start)
        ).id();
        UUID entry = bookingService.joinWaitlist(
            customerTwo.getId(), new WaitlistRequest(place.getId(), service.getId(), start)
        ).id();
        bookingService.cancel(customerOne.getId(), appointment);
        CyclicBarrier barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> accept = () -> {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                bookingService.acceptWaitlistOffer(customerTwo.getId(), entry);
                return true;
            };
            List<Future<Boolean>> results = executor.invokeAll(List.of(
                bookAfterBarrier(barrier, customerOne, service, start), accept
            ));
            assertFalse(results.getFirst().get());
            assertEquals(true, results.getLast().get());
            assertEquals(1, bookingService.myAppointments(customerTwo.getId()).size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aHoldReservesOnlyOneSeatWhenOtherCapacityBecomesAvailable() {
        place.update(place.getName(), null, place.getAddress(), 2, true);
        places.save(place);
        Offering service = saveService(30);
        Instant start = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        UUID first = bookingService.createAppointment(
            customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), start)
        ).id();
        UUID second = bookingService.createAppointment(
            customerTwo.getId(), new AppointmentRequest(place.getId(), service.getId(), start)
        ).id();
        UUID entry = bookingService.joinWaitlist(
            owner.getId(), new WaitlistRequest(place.getId(), service.getId(), start)
        ).id();
        bookingService.cancel(customerOne.getId(), first);
        bookingService.cancel(customerTwo.getId(), second);
        bookingService.createAppointment(customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), start));
        bookingService.acceptWaitlistOffer(owner.getId(), entry);
        assertFalse(bookingService.availability(place.getId(), service.getId(), start, start.plusSeconds(1800)).contains(start));
    }

    @Test
    void closedDateBlocksAvailabilityBookingAndWaitlist() {
        Offering service = saveService(30);
        Instant start = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        calendar.save(owner.getId(), place.getId(), bookingDate, new CalendarExceptionRequest(true, null, null));
        assertEquals(List.of(), bookingService.availability(place.getId(), service.getId(), start, start.plusSeconds(3600)));
        ResponseStatusException bookingError = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> bookingService.createAppointment(customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), start))
        );
        assertEquals(HttpStatus.BAD_REQUEST, bookingError.getStatusCode());
        ResponseStatusException waitlistError = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> bookingService.joinWaitlist(customerOne.getId(), new WaitlistRequest(place.getId(), service.getId(), start))
        );
        assertEquals(HttpStatus.BAD_REQUEST, waitlistError.getStatusCode());
        calendar.delete(owner.getId(), place.getId(), bookingDate);
        bookingService.createAppointment(customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), start));
    }

    @Test
    void specialHoursReplaceWeeklyHoursAndAnchorServiceSlots() {
        Offering service = saveService(30);
        calendar.save(owner.getId(), place.getId(), bookingDate,
            new CalendarExceptionRequest(false, LocalTime.of(12, 15), LocalTime.of(13, 15)));
        Instant first = bookingDate.atTime(12, 15).atZone(BUSINESS_ZONE).toInstant();
        assertEquals(List.of(first, first.plusSeconds(1800)), bookingService.availability(
            place.getId(), service.getId(), bookingDate.atStartOfDay(BUSINESS_ZONE).toInstant(),
            bookingDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()
        ));
        bookingService.createAppointment(customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), first));
        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> bookingService.createAppointment(customerTwo.getId(),
                new AppointmentRequest(place.getId(), service.getId(), first.plusSeconds(2700)))
        );
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void calendarChangesCannotInvalidateAppointmentsAndRollbackTheException() {
        Offering service = saveService(30);
        Instant start = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        UUID appointment = bookingService.createAppointment(
            customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), start)
        ).id();
        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> calendar.save(owner.getId(), place.getId(), bookingDate, new CalendarExceptionRequest(true, null, null))
        );
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(0, calendar.list(owner.getId(), place.getId()).size());
        var hour = businessHours.findByPlaceIdOrderByDayOfWeekAscOpensAtAsc(place.getId()).getFirst();
        org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
            () -> bookingService.deleteHour(owner.getId(), place.getId(), hour.getId()));
        assertEquals(1, bookingService.getHours(place.getId()).size());
        bookingService.cancel(customerOne.getId(), appointment);
        calendar.save(owner.getId(), place.getId(), bookingDate, new CalendarExceptionRequest(true, null, null));
    }

    @Test
    void activeOffersAlsoPreventClosingTheirDate() {
        Offering service = saveService(30);
        Instant start = bookingDate.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant();
        UUID appointment = bookingService.createAppointment(
            customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), start)
        ).id();
        UUID entry = bookingService.joinWaitlist(
            customerTwo.getId(), new WaitlistRequest(place.getId(), service.getId(), start)
        ).id();
        bookingService.cancel(customerOne.getId(), appointment);
        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
            () -> calendar.save(owner.getId(), place.getId(), bookingDate, new CalendarExceptionRequest(true, null, null)));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        bookingService.declineWaitlistOffer(customerTwo.getId(), entry);
        calendar.save(owner.getId(), place.getId(), bookingDate, new CalendarExceptionRequest(true, null, null));
    }

    @Test
    void removingSpecialHoursCannotStrandAnExistingAppointment() {
        Offering service = saveService(30);
        calendar.save(owner.getId(), place.getId(), bookingDate,
            new CalendarExceptionRequest(false, LocalTime.of(19, 0), LocalTime.of(20, 0)));
        Instant start = bookingDate.atTime(19, 0).atZone(BUSINESS_ZONE).toInstant();
        bookingService.createAppointment(customerOne.getId(), new AppointmentRequest(place.getId(), service.getId(), start));
        ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
            () -> calendar.delete(owner.getId(), place.getId(), bookingDate));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(1, calendar.list(owner.getId(), place.getId()).size());
    }

    @Test
    void calendarRejectsInvalidHoursAndOtherOwners() {
        org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
            () -> calendar.save(owner.getId(), place.getId(), bookingDate,
                new CalendarExceptionRequest(false, LocalTime.NOON, LocalTime.of(9, 0))));
        ResponseStatusException forbidden = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
            () -> calendar.save(customerOne.getId(), place.getId(), bookingDate,
                new CalendarExceptionRequest(true, null, null)));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
        assertEquals(0, calendar.list(owner.getId(), place.getId()).size());
    }

    private Callable<Boolean> bookAfterBarrier(
        CyclicBarrier barrier,
        User customer,
        Offering service,
        Instant startsAt
    ) {
        return () -> {
            barrier.await();
            try {
                bookingService.createAppointment(
                    customer.getId(),
                    new AppointmentRequest(place.getId(), service.getId(), startsAt)
                );
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
