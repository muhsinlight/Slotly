package com.slotly.api;

import com.slotly.api.module.booking.controller.AdminOverviewController;
import com.slotly.api.module.booking.dto.response.AppointmentResponse;
import com.slotly.api.module.booking.entity.Appointment;
import com.slotly.api.module.booking.repository.AppointmentRepository;
import com.slotly.api.module.booking.service.AvailabilityService;
import com.slotly.api.module.place.entity.BusinessHour;
import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.OfferingKind;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.entity.UserRole;
import com.slotly.api.module.waitlist.dto.response.WaitlistResponse;
import com.slotly.api.module.waitlist.entity.WaitlistEntry;
import com.slotly.api.module.waitlist.entity.WaitlistStatus;
import com.slotly.api.module.waitlist.repository.WaitlistRepository;
import com.slotly.api.module.waitlist.service.WaitlistService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("integration-test")
@Transactional
class ListFetchPlanIntegrationTests {
    private static final Instant START = Instant.parse("2030-01-10T10:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private WaitlistRepository waitlist;

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private AvailabilityService availability;

    @Autowired
    private AdminOverviewController admin;

    @ParameterizedTest
    @ValueSource(ints = {5, 30})
    void availabilityReadsSixQueriesForEitherSlotDensity(int minutes) {
        ZoneId zone = ZoneId.of("Europe/Istanbul");
        LocalDate day = LocalDate.now(zone).plusDays(2);
        Place place = place();
        Offering service = new Offering(place, "Measured", null, BigDecimal.TEN, minutes, OfferingKind.SERVICE);
        entityManager.persist(service);
        entityManager.persist(new BusinessHour(place, day.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.NOON));
        Instant start = day.atTime(9, 0).atZone(zone).toInstant();
        Instant end = day.atTime(12, 0).atZone(zone).toInstant();
        entityManager.persist(new Appointment(user(UserRole.CUSTOMER), place, service, start, start.plusSeconds(1800)));
        Statistics statistics = prepareMeasurement();

        var slots = availability.availability(place.getId(), service.getId(), start, end);
        assertEquals(150 / minutes, slots.size());
        assertEquals(start.plusSeconds(1800), slots.getFirst());
        assertEquals(6, statistics.getPrepareStatementCount());
    }

    @Test
    void adminSearchPagesBothListsInSqlWithGlobalCounts() {
        String name = "İŞLETME-%_" + UUID.randomUUID();
        Place place = new Place(user(UserRole.OWNER), name, null, "Address", 1);
        entityManager.persist(place);
        Offering service = offering(place, 1);
        User customer = user(UserRole.CUSTOMER);
        for (int index = 0; index < 5; index++) {
            Instant start = START.plusSeconds(index * 3600L);
            entityManager.persist(new Appointment(customer, place, service, start, start.plusSeconds(1800)));
            entityManager.persist(new WaitlistEntry(customer, place, service, start, start.plusSeconds(1800)));
        }
        Statistics statistics = prepareMeasurement();

        var page = admin.overview(1, 2, 2, name).getBody().getData();
        assertEquals(2, page.appointments().size());
        assertEquals(1, page.waitlist().size());
        assertEquals(START.plusSeconds(2 * 3600), page.appointments().getFirst().startsAt());
        assertEquals(5, page.appointmentTotal());
        assertEquals(5, page.waitlistTotal());
        assertTrue(page.waiting() >= 5);
        // A partially filled last page can infer its total without a count query.
        assertTrue(statistics.getPrepareStatementCount() <= 7);
    }

    @Test
    void customerReadOnlyTouchesTheirOwnListAndSchedulerClosesAtMostOneBatch() {
        Place place = place();
        Offering service = offering(place, 1);
        User customer = user(UserRole.CUSTOMER);
        Instant past = Instant.parse("1900-01-01T10:00:00Z");
        List<UUID> entryIds = new ArrayList<>();
        for (int index = 0; index < 102; index++) {
            Instant start = past.plusSeconds(index * 3600L);
            WaitlistEntry entry = new WaitlistEntry(customer, place, service, start, start.plusSeconds(1800));
            entityManager.persist(entry);
            entryIds.add(entry.getId());
        }
        User outsider = user(UserRole.CUSTOMER);
        Statistics statistics = prepareMeasurement();
        assertTrue(waitlistService.myWaitlist(outsider.getId()).isEmpty());
        assertEquals(1, statistics.getPrepareStatementCount());
        assertEquals(WaitlistStatus.WAITING, waitlist.findById(entryIds.getFirst()).orElseThrow().getStatus());

        waitlistService.expireOffers();
        assertEquals(100, waitlist.findAllById(entryIds).stream()
            .filter(entry -> entry.getStatus() == WaitlistStatus.EXPIRED).count());
        waitlistService.expireOffers();
        assertTrue(waitlist.findAllById(entryIds).stream().allMatch(entry -> entry.getStatus() == WaitlistStatus.EXPIRED));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void customerAppointmentsUseOneQueryAndMapAfterDetach(int size) {
        User customer = user(UserRole.CUSTOMER);
        for (int index = 0; index < size; index++) {
            Place place = place();
            Offering offering = offering(place, index);
            entityManager.persist(new Appointment(customer, place, offering,
                START.plusSeconds(index * 3600L), START.plusSeconds(index * 3600L + 1800)));
        }
        UUID customerId = customer.getId();
        Statistics statistics = prepareMeasurement();

        var rows = appointments.findByCustomerIdOrderByStartsAtDesc(customerId);
        entityManager.clear();
        var responses = rows.stream().map(AppointmentResponse::from).toList();

        assertEquals(size, responses.size());
        assertEquals(START.plusSeconds((size - 1) * 3600L), responses.getFirst().startsAt());
        assertEquals("Test Customer", responses.getFirst().customerName());
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void ownerAppointmentsUseOneQueryForDistinctCustomersAndOfferings(int size) {
        Place place = place();
        for (int index = 0; index < size; index++) {
            entityManager.persist(new Appointment(user(UserRole.CUSTOMER), place, offering(place, index),
                START.plusSeconds(index * 3600L), START.plusSeconds(index * 3600L + 1800)));
        }
        UUID placeId = place.getId();
        Statistics statistics = prepareMeasurement();

        var rows = appointments.findByPlaceIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
            placeId, START, START.plusSeconds(size * 3600L));
        entityManager.clear();
        var responses = rows.stream().map(AppointmentResponse::from).toList();

        assertEquals(size, responses.size());
        assertEquals(START, responses.getFirst().startsAt());
        assertEquals(size, responses.stream().map(AppointmentResponse::customerId).distinct().count());
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void customerWaitlistUsesOneQueryAndMapsAfterDetach(int size) {
        User customer = user(UserRole.CUSTOMER);
        for (int index = 0; index < size; index++) {
            Place place = place();
            entityManager.persist(new WaitlistEntry(customer, place, offering(place, index),
                START.plusSeconds(index * 3600L), START.plusSeconds(index * 3600L + 1800)));
        }
        UUID customerId = customer.getId();
        Statistics statistics = prepareMeasurement();

        var rows = waitlist.findByCustomerIdOrderByCreatedAtDesc(customerId);
        entityManager.clear();
        var responses = rows.stream().map(WaitlistResponse::from).toList();

        assertEquals(size, responses.size());
        assertFalse(responses.getFirst().placeName().isBlank());
        assertFalse(responses.getFirst().offeringName().isBlank());
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    private Statistics prepareMeasurement() {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    private User user(UserRole role) {
        User user = new User(UUID.randomUUID().toString(), "hash", "Test", "Customer", role);
        entityManager.persist(user);
        return user;
    }

    private Place place() {
        Place place = new Place(user(UserRole.OWNER), "Fetch Place", null, "Address", 1);
        entityManager.persist(place);
        return place;
    }

    private Offering offering(Place place, int index) {
        Offering offering = new Offering(place, "Service " + index, null, BigDecimal.TEN, 30, OfferingKind.SERVICE);
        entityManager.persist(offering);
        return offering;
    }
}
