package com.slotly.api;

import com.slotly.api.module.booking.dto.response.AppointmentResponse;
import com.slotly.api.module.booking.entity.Appointment;
import com.slotly.api.module.booking.repository.AppointmentRepository;
import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.OfferingKind;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.entity.UserRole;
import com.slotly.api.module.waitlist.dto.response.WaitlistResponse;
import com.slotly.api.module.waitlist.entity.WaitlistEntry;
import com.slotly.api.module.waitlist.repository.WaitlistRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
