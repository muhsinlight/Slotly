package com.slotly.api.module.auth.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.slotly.api.common.exception.ApiException;

@Service
public class PasswordRecoveryService {
    private final JdbcTemplate jdbc;
    private final AccountEmailService emails;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public PasswordRecoveryService(JdbcTemplate jdbc, AccountEmailService emails, PasswordEncoder passwords, Clock clock) {
        this.jdbc = jdbc;
        this.emails = emails;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional
    public void request(String address) {
        String email = address.strip().toLowerCase(Locale.ROOT);
        String identifier = AccountEmailService.hash(email);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", "reset:" + identifier);
        int allowed = jdbc.update("""
            INSERT INTO password_reset_limits(id, last_requested_at) VALUES (?, ?)
            ON CONFLICT (id) DO UPDATE SET last_requested_at = excluded.last_requested_at
            WHERE password_reset_limits.last_requested_at <= excluded.last_requested_at - interval '1 minute'
            """, identifier, Timestamp.from(clock.instant()));
        if (allowed == 0) {
            return;
        }
        var rows = jdbc.queryForList("SELECT id FROM account_emails WHERE email = ? AND verified = true", email);
        if (rows.isEmpty()) {
            return;
        }
        UUID userId = (UUID) rows.getFirst().get("id");
        emails.lockUser(userId);
        // Recheck after taking the account lock: the address may have changed meanwhile.
        var current = emails.status(userId);
        if (!current.verified() || !email.equals(current.email())) {
            return;
        }
        emails.issueToken(userId, email, "RESET_PASSWORD", "/reset-password", Duration.ofMinutes(15));
    }

    @Transactional
    public void reset(String token, String password) {
        String hash = AccountEmailService.hash(token);
        var rows = jdbc.queryForList(
            "SELECT user_id FROM account_tokens WHERE token_hash = ? AND purpose = 'RESET_PASSWORD'", hash);
        if (rows.isEmpty()) {
            throw invalidToken();
        }
        UUID userId = (UUID) rows.getFirst().get("user_id");
        emails.lockUser(userId);
        var consumed = jdbc.queryForList("""
            UPDATE account_tokens t SET consumed_at = ? WHERE t.token_hash = ?
                AND t.purpose = 'RESET_PASSWORD' AND t.consumed_at IS NULL AND t.expires_at > ?
                AND EXISTS (SELECT 1 FROM account_emails e
                    WHERE e.id = t.user_id AND e.email = t.destination AND e.verified = true)
            RETURNING user_id
            """, Timestamp.from(clock.instant()), hash, Timestamp.from(clock.instant()));
        if (consumed.isEmpty()) {
            throw invalidToken();
        }
        jdbc.update("UPDATE app_users SET password_hash = ?, auth_version = auth_version + 1 WHERE id = ?",
            passwords.encode(password), userId);
        jdbc.update("UPDATE account_tokens SET consumed_at = ? WHERE user_id = ? AND purpose = 'RESET_PASSWORD'",
            Timestamp.from(clock.instant()), userId);
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Bağlantı geçersiz veya süresi dolmuş.");
    }
}
