package com.slotly.api.module.notification.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentReminderService {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final Clock clock;
    private final String frontendUrl;
    private final ZoneId zone;

    public AppointmentReminderService(JdbcTemplate jdbc, NotificationService notifications, Clock clock,
        @Value("${slotly.frontend.url:http://localhost:5173}") String frontendUrl,
        @Value("${slotly.business.time-zone:Europe/Istanbul}") String zone) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
        this.zone = ZoneId.of(zone);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void queueDue() {
        Instant now = clock.instant();
        var rows = jdbc.queryForList("""
            SELECT a.id, a.customer_id, a.starts_at, e.email, p.name AS place_name, o.name AS offering_name
            FROM appointments a JOIN account_emails e ON e.id = a.customer_id
                JOIN notification_preferences n ON n.id = e.id
                JOIN places p ON p.id = a.place_id JOIN offerings o ON o.id = a.offering_id
            WHERE a.status = 'CONFIRMED' AND e.verified = true AND n.reminder_email = true
                AND a.starts_at > ? AND a.starts_at <= ?::timestamptz + n.reminder_hours * interval '1 hour'
                AND NOT EXISTS (SELECT 1 FROM email_outbox q WHERE q.kind = 'REMINDER'
                    AND q.reference_id = a.id AND q.expires_at = a.starts_at)
            ORDER BY a.starts_at LIMIT 200 FOR UPDATE OF a SKIP LOCKED
            """, Timestamp.from(now), Timestamp.from(now));
        for (var row : rows) {
            UUID appointmentId = (UUID) row.get("id");
            Instant start = ((Timestamp) row.get("starts_at")).toInstant();
            String body = row.get("place_name") + " · " + row.get("offering_name") + "\n"
                + start.atZone(zone).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                + "\nRandevuların: " + frontendUrl + "/appointments";
            notifications.enqueue((UUID) row.get("customer_id"), (String) row.get("email"),
                "Slotly: yaklaşan randevun", body, "reminder:" + appointmentId + ":" + start,
                "REMINDER", appointmentId, start);
        }
    }
}
