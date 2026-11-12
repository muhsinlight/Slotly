package com.slotly.api;

import com.slotly.api.dto.request.AppointmentRequest;
import com.slotly.api.dto.request.WaitlistRequest;
import com.slotly.api.entity.Appointment;
import com.slotly.api.entity.AppointmentStatus;
import com.slotly.api.entity.BusinessHour;
import com.slotly.api.entity.Offering;
import com.slotly.api.entity.OfferingKind;
import com.slotly.api.entity.Place;
import com.slotly.api.entity.User;
import com.slotly.api.entity.UserRole;
import com.slotly.api.repository.AppointmentRepository;
import com.slotly.api.repository.BusinessHourRepository;
import com.slotly.api.repository.OfferingRepository;
import com.slotly.api.repository.PlaceRepository;
import com.slotly.api.repository.UserRepository;
import com.slotly.api.repository.WaitlistRepository;
import com.slotly.api.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
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
@Import(BookingServiceIntegrationTests.TestClockConfiguration.class)
class BookingServiceIntegrationTests {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Istanbul");

    @Autowired
    private BookingService bookingService;

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

