package com.slotly.api;

import com.slotly.api.module.auth.service.AccountEmailService;
import com.slotly.api.module.booking.dto.request.AppointmentRequest;
import com.slotly.api.module.booking.service.BookingService;
import com.slotly.api.module.notification.service.*;
import com.slotly.api.module.place.entity.*;
import com.slotly.api.module.place.repository.*;
import com.slotly.api.module.user.entity.*;
import com.slotly.api.module.user.repository.UserRepository;
import com.slotly.api.module.waitlist.dto.request.WaitlistRequest;
import com.slotly.api.module.waitlist.service.impl.WaitlistOfferEvent;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration-test")
@Import(NotificationIntegrationTests.GatewayConfig.class)
class NotificationIntegrationTests {
    @Autowired
    private BookingService bookings;
    @Autowired
    private AccountEmailService emails;
    @Autowired
    private NotificationService notifications;
    @Autowired
    private EmailDispatcher dispatcher;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PlaceRepository places;
    @Autowired
    private OfferingRepository offerings;
    @Autowired
    private BusinessHourRepository hours;
    @Autowired
    private FakeGateway gateway;
    @Autowired
    private PlatformTransactionManager transactions;

    @TestConfiguration
    static class GatewayConfig {
        @Bean
        FakeGateway gateway() {
            return new FakeGateway();
        }
    }

    static class FakeGateway implements EmailGateway {
        final Set<UUID> delivered = new HashSet<>();
        boolean fail;

        public void send(UUID messageId, String recipient, String subject, String body) {
            if (fail) {
                throw new IllegalStateException("Synthetic provider failure");
            }
            delivered.add(messageId);
        }
    }

    @Test
    void activeOfferIsQueuedOnceAndDeliveredOnce() {
        var offer = offer(true);
        new TransactionTemplate(transactions).executeWithoutResult(status -> notifications.onOffer(offer));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE deduplication_key = ?",
            Integer.class, "offer:" + offer.entryId()));
        drain();
        assertEquals("SENT", state(offer.entryId()));
        UUID id = messageId(offer.entryId());
        assertTrue(gateway.delivered.contains(id));
        drain();
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM email_outbox WHERE id = ?", Integer.class, id));
    }

    @Test
    void declinedOfferAndRevokedConsentPreventDelivery() {
        var declined = offer(true);
        bookings.declineWaitlistOffer(declined.customerId(), declined.entryId());
        var optedOut = offer(true);
        notifications.savePreferences(optedOut.customerId(), new NotificationService.Preferences(false, false));
        drain();
        assertEquals("CANCELLED", state(declined.entryId()));
        assertEquals("CANCELLED", state(optedOut.entryId()));
        assertFalse(gateway.delivered.contains(messageId(declined.entryId())));
    }

    @Test
    void deliveryFailureRetriesWithSameId() {
        var offer = offer(true);
        gateway.fail = true;
        try {
            drain();
            assertEquals("PENDING", state(offer.entryId()));
        } finally {
            gateway.fail = false;
        }
        UUID id = messageId(offer.entryId());
        jdbc.update("UPDATE email_outbox SET next_attempt_at = now() - interval '1 second' WHERE id = ?", id);
        drain();
        assertEquals("SENT", state(offer.entryId()));
        assertEquals(2, jdbc.queryForObject("SELECT attempts FROM email_outbox WHERE id = ?", Integer.class, id));
    }

    @Test
    void emailNotificationsRequireVerificationAndOptIn() {
        User user = user();
        assertThrows(ResponseStatusException.class,
            () -> notifications.savePreferences(user.getId(), new NotificationService.Preferences(true, false)));
        var offer = offer(false);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE deduplication_key = ?",
            Integer.class, "offer:" + offer.entryId()));
    }

    private WaitlistOfferEvent offer(boolean optIn) {
        User owner = users.save(new User("owner-" + suffix(), "unused", "Owner", "Test", UserRole.OWNER));
        User booked = user();
        User waiting = user();
        String address = suffix() + "@example.test";
        emails.requestVerification(waiting.getId(), address);
        String body = jdbc.queryForObject("SELECT body FROM email_outbox WHERE user_id = ?", String.class, waiting.getId());
        emails.verify(body.substring(body.indexOf("#token=") + 7));
        if (optIn) notifications.savePreferences(waiting.getId(), new NotificationService.Preferences(true, false));
        Place place = places.save(new Place(owner, "Test place", null, "Test", 1));
        Offering offering = offerings.save(new Offering(place, "Service", null, BigDecimal.TEN, 30, OfferingKind.SERVICE));
        LocalDate day = LocalDate.now(ZoneId.of("Europe/Istanbul")).plusDays(3);
        hours.save(new BusinessHour(place, day.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(18, 0)));
        Instant start = day.atTime(12, 0).atZone(ZoneId.of("Europe/Istanbul")).toInstant();
        var appointment = bookings.createAppointment(booked.getId(), new AppointmentRequest(place.getId(), offering.getId(), start));
        var entry = bookings.joinWaitlist(waiting.getId(), new WaitlistRequest(place.getId(), offering.getId(), start));
        bookings.cancel(booked.getId(), appointment.id());
        var offered = bookings.myWaitlist(waiting.getId()).getFirst();
        return new WaitlistOfferEvent(waiting.getId(), entry.id(), offered.offerExpiresAt());
    }

    private User user() {
        return users.save(new User("notify-" + suffix(), "unused", "Notify", "Test", UserRole.CUSTOMER));
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void drain() {
        for (int count = 0; count < 500 && dispatcher.deliverNext(); count++) {
        }
    }

    private UUID messageId(UUID entryId) {
        return jdbc.queryForObject("SELECT id FROM email_outbox WHERE deduplication_key = ?", UUID.class, "offer:" + entryId);
    }

    private String state(UUID entryId) {
        return jdbc.queryForObject("SELECT state FROM email_outbox WHERE deduplication_key = ?", String.class, "offer:" + entryId);
    }
}
