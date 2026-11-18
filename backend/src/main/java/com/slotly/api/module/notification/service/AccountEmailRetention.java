package com.slotly.api.module.notification.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;

@Component
public class AccountEmailRetention {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AccountEmailRetention(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    public void clearInactiveLinks() {
        jdbc.update("""
            WITH obsolete AS (
                SELECT o.id FROM email_outbox o
                WHERE o.kind = 'ACCOUNT' AND o.body <> '' AND (
                    o.state <> 'PENDING' OR NOT EXISTS (
                        SELECT 1 FROM account_tokens t WHERE t.id = o.reference_id
                            AND t.consumed_at IS NULL AND t.expires_at > ?
                    )
                )
                ORDER BY o.created_at, o.id LIMIT 500 FOR UPDATE OF o SKIP LOCKED
            )
            UPDATE email_outbox o SET body = '',
                state = CASE WHEN o.state = 'PENDING' THEN 'CANCELLED' ELSE o.state END
            FROM obsolete WHERE o.id = obsolete.id
            """, Timestamp.from(clock.instant()));
    }
}
