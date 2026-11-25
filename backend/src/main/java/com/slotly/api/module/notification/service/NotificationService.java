package com.slotly.api.module.notification.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import com.slotly.api.module.waitlist.service.impl.WaitlistOfferEvent;
import com.slotly.api.module.waitlist.repository.WaitlistRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.slotly.api.common.exception.ApiException;

@Service
public class NotificationService {
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final JdbcTemplate jdbc;
    private final WaitlistRepository waitlist;
    private final Clock clock;
    private final String frontendUrl;
    private final ZoneId zone;

    public NotificationService(
        JdbcTemplate jdbc,
        WaitlistRepository waitlist,
        Clock clock,
        @Value("${slotly.frontend.url:http://localhost:5173}") String frontendUrl,
        @Value("${slotly.business.time-zone:Europe/Istanbul}") String zone
    ) {
        this.jdbc = jdbc;
        this.waitlist = waitlist;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
        this.zone = ZoneId.of(zone);
    }

    public record Preferences(boolean offerEmail, boolean reminderEmail, Integer reminderHours) {
        public Preferences(boolean offerEmail, boolean reminderEmail) {
            this(offerEmail, reminderEmail, 24);
        }

        public Preferences {
            if (reminderHours == null) {
                reminderHours = 24;
            }
        }
    }

    public record Delivery(UUID id, String subject, String state, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public Preferences preferences(UUID userId) {
        return jdbc.query("SELECT offer_email, reminder_email, reminder_hours FROM notification_preferences WHERE id = ?",
            (row, index) -> new Preferences(row.getBoolean(1), row.getBoolean(2), row.getInt(3)), userId)
            .stream().findFirst().orElse(new Preferences(false, false));
    }

    @Transactional
    public void savePreferences(UUID userId, Preferences preferences) {
        if (preferences.reminderHours() != 2 && preferences.reminderHours() != 24) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Hatırlatma 2 veya 24 saat önce olabilir.");
        }
        jdbc.queryForList("SELECT id FROM app_users WHERE id = ? FOR UPDATE", userId);
        if ((preferences.offerEmail() || preferences.reminderEmail()) && jdbc.queryForObject(
            "SELECT count(*) FROM account_emails WHERE id = ? AND verified = true", Integer.class, userId) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Önce e-posta adresini doğrula.");
        }
        jdbc.update("""
            INSERT INTO notification_preferences(id, offer_email, reminder_email, reminder_hours) VALUES (?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET offer_email = excluded.offer_email,
                reminder_email = excluded.reminder_email, reminder_hours = excluded.reminder_hours
            """, userId, preferences.offerEmail(), preferences.reminderEmail(), preferences.reminderHours());
    }

    @Transactional(readOnly = true)
    public List<Delivery> deliveries(UUID userId) {
        return jdbc.query("""
            SELECT id, subject, state, created_at FROM email_outbox
            WHERE user_id = ? ORDER BY created_at DESC LIMIT 20
            """, (row, index) -> new Delivery(row.getObject(1, UUID.class), row.getString(2),
                row.getString(3), row.getTimestamp(4).toInstant()), userId);
    }

    @EventListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void onOffer(WaitlistOfferEvent event) {
        var entry = waitlist.findById(event.entryId()).orElseThrow();
        var addresses = jdbc.queryForList("""
            SELECT e.email FROM account_emails e JOIN notification_preferences p ON p.id = e.id
            WHERE e.id = ? AND e.verified = true AND p.offer_email = true
            """, event.customerId());
        if (addresses.isEmpty()) {
            return;
        }
        String startFormatted = entry.getRequestedStartsAt().atZone(zone).format(LOCAL_DATE_TIME);
        String deadlineFormatted = event.offerExpiresAt().atZone(zone).format(LOCAL_DATE_TIME);
        String body = entry.getPlace().getName() + " · " + entry.getOffering().getName()
            + "\nSaat: " + startFormatted + "\nSon kabul: " + deadlineFormatted
            + "\nTeklifi incele: " + frontendUrl + "/waitlist";
        enqueue(event.customerId(), (String) addresses.getFirst().get("email"), "Slotly: randevu teklifin var",
            body, "offer:" + event.entryId(), "OFFER", event.entryId(), event.offerExpiresAt());
    }

    public void enqueue(UUID userId, String recipient, String subject, String body,
        String key, String kind, UUID referenceId, Instant expiresAt) {
        jdbc.update("""
            INSERT INTO email_outbox(id, user_id, recipient, subject, body, deduplication_key,
                kind, reference_id, expires_at, next_attempt_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (deduplication_key) DO NOTHING
            """, UUID.randomUUID(), userId, recipient, subject, body, key, kind, referenceId,
            Timestamp.from(expiresAt), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
    }
}
