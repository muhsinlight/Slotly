package com.slotly.api.module.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.slotly.api.common.exception.ApiException;

@Service
public class AccountEmailService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public AccountEmailService(
        JdbcTemplate jdbc,
        Clock clock,
        @Value("${slotly.frontend.url:http://localhost:5173}") String frontendUrl
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
    }

    public record EmailStatus(String email, boolean verified, String pendingEmail) {
    }

    @Transactional(readOnly = true)
    public EmailStatus status(UUID userId) {
        return jdbc.query("""
            SELECT COALESCE(a.email, p.destination) AS email, a.verified, p.destination
            FROM account_emails a
            LEFT JOIN account_tokens p ON p.user_id = a.id AND p.purpose = 'VERIFY_EMAIL'
                AND p.consumed_at IS NULL AND p.expires_at > ?
            WHERE a.id = ?
            """, (row, index) -> new EmailStatus(
                row.getString("email"), row.getBoolean("verified"), row.getString("destination")
            ), Timestamp.from(clock.instant()), userId)
            .stream().findFirst().orElse(new EmailStatus(null, false, null));
    }

    @Transactional
    public void requestVerification(UUID userId, String address) {
        lockUser(userId);
        String email = address.strip().toLowerCase(Locale.ROOT);
        lockAddress(email);
        Instant now = clock.instant();
        var previous = jdbc.queryForList("SELECT last_requested_at FROM account_emails WHERE id = ?", userId);
        if (!previous.isEmpty()) {
            Instant last = ((Timestamp) previous.getFirst().get("last_requested_at")).toInstant();
            if (last.plusSeconds(60).isAfter(now)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Bir dakika sonra yeniden dene.");
            }
        }
        boolean claimed = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM account_emails WHERE email = ? AND id <> ?)
                OR EXISTS (SELECT 1 FROM account_tokens WHERE destination = ? AND user_id <> ?
                    AND purpose = 'VERIFY_EMAIL' AND consumed_at IS NULL AND expires_at > ?)
            """, Boolean.class, email, userId, email, userId, Timestamp.from(now)));
        if (claimed) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu adres kullanılamıyor.");
        }
        jdbc.update("""
            INSERT INTO account_emails(id, last_requested_at) VALUES (?, ?)
            ON CONFLICT (id) DO UPDATE SET last_requested_at = excluded.last_requested_at
            """, userId, Timestamp.from(now));
        issueToken(userId, email, "VERIFY_EMAIL", "/verify-email", Duration.ofMinutes(30));
    }

    @Transactional
    public void verify(String token) {
        String hash = hash(token);
        var candidates = jdbc.queryForList(
            "SELECT user_id, destination FROM account_tokens WHERE token_hash = ? AND purpose = 'VERIFY_EMAIL'", hash);
        if (candidates.isEmpty()) {
            throw invalidToken();
        }
        UUID userId = (UUID) candidates.getFirst().get("user_id");
        String email = (String) candidates.getFirst().get("destination");
        lockUser(userId);
        lockAddress(email);
        Timestamp now = Timestamp.from(clock.instant());
        int consumed = jdbc.update("""
            UPDATE account_tokens SET consumed_at = ? WHERE token_hash = ? AND purpose = 'VERIFY_EMAIL'
                AND consumed_at IS NULL AND expires_at > ?
            """, now, hash, now);
        if (consumed == 0) {
            throw invalidToken();
        }
        int updated = jdbc.update("UPDATE account_emails SET email = ?, verified = true WHERE id = ?", email, userId);
        if (updated == 0) {
            throw invalidToken();
        }
        jdbc.update("""
            UPDATE account_tokens SET consumed_at = ? WHERE user_id = ? AND purpose = 'RESET_PASSWORD'
                AND destination <> ? AND consumed_at IS NULL
            """, now, userId, email);
    }

    private void lockAddress(String email) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(1, hashtext(?))", email);
    }

    void lockUser(UUID userId) {
        if (jdbc.queryForList("SELECT id FROM app_users WHERE id = ? FOR UPDATE", userId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Hesap bulunamadı.");
        }
    }

    void issueToken(UUID userId, String email, String purpose, String path, Duration lifetime) {
        Instant now = clock.instant();
        jdbc.update("UPDATE account_tokens SET consumed_at = ? WHERE user_id = ? AND purpose = ? AND consumed_at IS NULL",
            Timestamp.from(now), userId, purpose);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id = UUID.randomUUID();
        Timestamp expiresAt = Timestamp.from(now.plus(lifetime));
        jdbc.update("""
            INSERT INTO account_tokens(id, user_id, token_hash, purpose, destination, expires_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """, id, userId, hash(token), purpose, email, expiresAt);
        jdbc.update("""
            INSERT INTO email_outbox(id, user_id, recipient, subject, body, deduplication_key,
                reference_id, expires_at, next_attempt_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), userId, email,
            purpose.equals("VERIFY_EMAIL") ? "Slotly e-posta doğrulama" : "Slotly parola sıfırlama",
            frontendUrl + path + "#token=" + token, "account:" + id, id, expiresAt, Timestamp.from(now), Timestamp.from(now));
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Bağlantı geçersiz veya süresi dolmuş.");
    }
}
