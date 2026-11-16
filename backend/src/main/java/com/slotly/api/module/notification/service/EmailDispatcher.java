package com.slotly.api.module.notification.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDispatcher {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectProvider<EmailGateway> gateway;

    public EmailDispatcher(JdbcTemplate jdbc, Clock clock, ObjectProvider<EmailGateway> gateway) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.gateway = gateway;
    }

    @Transactional
    public boolean deliverNext() {
        EmailGateway provider = gateway.getIfAvailable();
        if (provider == null) {
            return false;
        }
        var rows = jdbc.queryForList("""
            SELECT * FROM email_outbox WHERE state = 'PENDING' AND next_attempt_at <= ?
            ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1
            """, Timestamp.from(clock.instant()));
        if (rows.isEmpty()) {
            return false;
        }
        var message = rows.getFirst();
        UUID id = (UUID) message.get("id");
        if (!eligible(id)) {
            jdbc.update("UPDATE email_outbox SET state = 'CANCELLED', body = '' WHERE id = ?", id);
            return true;
        }
        int attempts = ((Number) message.get("attempts")).intValue() + 1;
        try {
            provider.send(id, (String) message.get("recipient"), (String) message.get("subject"), (String) message.get("body"));
            jdbc.update("UPDATE email_outbox SET state = 'SENT', sent_at = ?, attempts = ?, body = '' WHERE id = ?",
                Timestamp.from(clock.instant()), attempts, id);
        } catch (RuntimeException exception) {
            jdbc.update("UPDATE email_outbox SET state = ?, attempts = ?, next_attempt_at = ? WHERE id = ?",
                attempts >= 5 ? "FAILED" : "PENDING", attempts,
                Timestamp.from(clock.instant().plusSeconds(Math.min(3600, 60L << (attempts - 1)))), id);
        }
        return true;
    }

    private boolean eligible(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT CASE WHEN o.kind = 'ACCOUNT' THEN EXISTS (
                SELECT 1 FROM account_tokens t WHERE o.deduplication_key = 'account:' || t.id
                    AND t.consumed_at IS NULL AND t.expires_at > ?
            ) WHEN o.kind = 'OFFER' THEN EXISTS (
                SELECT 1 FROM waitlist_entries w
                JOIN account_emails e ON e.id = w.customer_id
                JOIN notification_preferences p ON p.id = e.id
                WHERE w.id = o.reference_id AND w.status = 'OFFERED' AND w.offer_expires_at > ?
                    AND e.verified = true AND e.email = o.recipient AND p.offer_email = true
            ) ELSE false END FROM email_outbox o WHERE o.id = ?
            """, Boolean.class, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), id));
    }
}
