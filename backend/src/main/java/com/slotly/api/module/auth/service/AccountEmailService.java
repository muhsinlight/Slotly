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
import org.springframework.web.server.ResponseStatusException;

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

    public record EmailStatus(String email, boolean verified) {
    }

    @Transactional(readOnly = true)
    public EmailStatus status(UUID userId) {
        return jdbc.query("SELECT email, verified FROM account_emails WHERE id = ?",
            (row, index) -> new EmailStatus(row.getString("email"), row.getBoolean("verified")), userId)
            .stream().findFirst().orElse(new EmailStatus(null, false));
    }

    @Transactional
    public void requestVerification(UUID userId, String address) {
        lockUser(userId);
        String email = address.strip().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        var previous = jdbc.queryForList("SELECT last_requested_at FROM account_emails WHERE id = ?", userId);
        if (!previous.isEmpty()) {
            Instant last = ((Timestamp) previous.getFirst().get("last_requested_at")).toInstant();
            if (last.plusSeconds(60).isAfter(now)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Bir dakika sonra yeniden dene.");
            }
        }
        // The advisory lock serializes competing claims for the same normalized address.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", email);
        Integer claimed = jdbc.queryForObject(
            "SELECT count(*) FROM account_emails WHERE email = ? AND id <> ?", Integer.class, email, userId);
        if (claimed != null && claimed > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu adres kullanılamıyor.");
        }
        jdbc.update("""
            INSERT INTO account_emails(id, email, verified, last_requested_at) VALUES (?, ?, false, ?)
            ON CONFLICT (id) DO UPDATE SET email = excluded.email,
                verified = false, last_requested_at = excluded.last_requested_at
            """, userId, email, Timestamp.from(now));
        issueToken(userId, email, "VERIFY_EMAIL", "/verify-email", Duration.ofMinutes(30));
    }

    @Transactional
    public void verify(String token) {
        String hash = hash(token);
        var candidates = jdbc.queryForList(
            "SELECT user_id FROM account_tokens WHERE token_hash = ? AND purpose = 'VERIFY_EMAIL'", hash);
        if (candidates.isEmpty()) {
            throw invalidToken();
        }
        UUID userId = (UUID) candidates.getFirst().get("user_id");
        lockUser(userId);
        var rows = jdbc.queryForList("""
            UPDATE account_tokens SET consumed_at = ? WHERE token_hash = ? AND purpose = 'VERIFY_EMAIL'
                AND consumed_at IS NULL AND expires_at > ? RETURNING destination
            """, Timestamp.from(clock.instant()), hash, Timestamp.from(clock.instant()));
        if (rows.isEmpty()) {
            throw invalidToken();
        }
        int updated = jdbc.update("UPDATE account_emails SET verified = true WHERE id = ? AND email = ?",
            userId, rows.getFirst().get("destination"));
        if (updated != 1) {
            throw invalidToken();
        }
    }

    private void lockUser(UUID userId) {
        if (jdbc.queryForList("SELECT id FROM app_users WHERE id = ? FOR UPDATE", userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hesap bulunamadı.");
        }
    }

    private void issueToken(UUID userId, String email, String purpose, String path, Duration lifetime) {
        Instant now = clock.instant();
        jdbc.update("UPDATE account_tokens SET consumed_at = ? WHERE user_id = ? AND purpose = ? AND consumed_at IS NULL",
            Timestamp.from(now), userId, purpose);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO account_tokens(id, user_id, token_hash, purpose, destination, expires_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """, id, userId, hash(token), purpose, email, Timestamp.from(now.plus(lifetime)));
        jdbc.update("""
            INSERT INTO email_outbox(id, user_id, recipient, subject, body, deduplication_key,
                next_attempt_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), userId, email, "Slotly e-posta doğrulama",
            frontendUrl + path + "#token=" + token, "account:" + id, Timestamp.from(now), Timestamp.from(now));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bağlantı geçersiz veya süresi dolmuş.");
    }
}
