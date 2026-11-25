package com.slotly.api;

import com.slotly.api.module.auth.service.AccountEmailService;
import com.slotly.api.module.notification.service.AccountEmailRetention;
import com.slotly.api.module.notification.service.NotificationService;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.entity.UserRole;
import com.slotly.api.module.user.repository.UserRepository;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AccountEmailIntegrationTests {
    @Autowired
    private AccountEmailService service;
    @Autowired
    private AccountEmailRetention retention;
    @Autowired
    private NotificationService notifications;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    private UUID userId;
    private String email;

    @BeforeEach
    void createUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        userId = users.save(new User("email-" + suffix, "unused", "Email", "Test", UserRole.CUSTOMER)).getId();
        email = suffix + "@example.test";
    }

    @Test
    void verificationIsSingleUseAndDoesNotExposeTokenInStatus() {
        service.requestVerification(userId, email.toUpperCase());
        assertFalse(service.status(userId).verified());
        assertEquals(email, service.status(userId).email());
        assertEquals(email, service.status(userId).pendingEmail());
        String token = latestToken();
        service.verify(token);
        assertTrue(service.status(userId).verified());
        assertNull(service.status(userId).pendingEmail());
        assertThrows(ResponseStatusException.class, () -> service.verify(token));
    }

    @Test
    void resendIsLimitedAndReplacesOldToken() {
        service.requestVerification(userId, email);
        String old = latestToken();
        assertEquals(429, assertThrows(ResponseStatusException.class,
            () -> service.requestVerification(userId, email)).getStatusCode().value());
        jdbc.update("UPDATE account_emails SET last_requested_at = now() - interval '2 minutes' WHERE id = ?", userId);
        service.requestVerification(userId, email);
        assertThrows(ResponseStatusException.class, () -> service.verify(old));
        service.verify(latestToken());
    }

    @Test
    void aPendingAddressIsReservedUntilItsTokenExpires() {
        service.requestVerification(userId, email);
        String token = latestToken();
        UUID other = otherUser();
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> service.requestVerification(other, email)).getStatusCode().value());
        jdbc.update("UPDATE account_tokens SET expires_at = now() - interval '1 second' WHERE user_id = ?", userId);
        service.requestVerification(other, email);
        assertEquals(email, service.status(other).pendingEmail());
        assertNull(service.status(userId).email());
        assertThrows(ResponseStatusException.class, () -> service.verify(token));
    }

    @Test
    void onlyOneConcurrentRequestCanReserveTheSameAddress() throws Exception {
        var start = new CountDownLatch(1);
        UUID other = otherUser();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(claimWhenReady(start, userId));
            var second = executor.submit(claimWhenReady(start, other));
            start.countDown();
            int firstStatus = first.get(10, TimeUnit.SECONDS);
            int secondStatus = second.get(10, TimeUnit.SECONDS);
            assertTrue((firstStatus == 200 && secondStatus == 409) || (firstStatus == 409 && secondStatus == 200));
        }
        assertEquals(1, jdbc.queryForObject("""
            SELECT count(*) FROM account_tokens WHERE purpose = 'VERIFY_EMAIL' AND destination = ?
            """, Integer.class, email));
    }

    @Test
    void cleanupRemovesExpiredAndLegacyFailedLinksWhileSendingIsOff() {
        service.requestVerification(userId, email);
        UUID other = otherUser();
        service.requestVerification(other, "failed-" + email);
        var original = notifications.deliveries(userId).getFirst();
        jdbc.update("UPDATE account_tokens SET expires_at = now() - interval '1 second' WHERE user_id = ?", userId);
        jdbc.update("UPDATE email_outbox SET state = 'FAILED', attempts = 5 WHERE user_id = ?", other);

        retention.clearInactiveLinks();
        var expired = notifications.deliveries(userId).getFirst();
        assertEquals("CANCELLED", expired.state());
        assertEquals(original.id(), expired.id());
        assertEquals(original.subject(), expired.subject());
        assertEquals(original.createdAt(), expired.createdAt());
        assertEquals("FAILED", notifications.deliveries(other).getFirst().state());
        assertEquals(2, jdbc.queryForObject("""
            SELECT count(*) FROM email_outbox WHERE user_id IN (?, ?) AND body = ''
            """, Integer.class, userId, other));
    }

    @Test
    void migrationPreservesVerifiedContactsPendingTokensAndQueuedLinks() throws Exception {
        String schema = "email_" + UUID.randomUUID().toString().replace("-", "");
        var source = jdbc.getDataSource();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                var config = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema);
                config.target("6").load().migrate();
                var legacy = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                UUID waiting = UUID.randomUUID();
                UUID token = UUID.randomUUID();
                UUID message = UUID.randomUUID();
                var pendingEmail = "pending-" + email;
                legacy.update("""
                    INSERT INTO app_users(id, username, first_name, last_name, password_hash, role)
                    VALUES (?, 'verified', 'Old', 'User', 'unused', 'CUSTOMER'),
                           (?, 'waiting', 'Old', 'User', 'unused', 'CUSTOMER')
                    """, userId, waiting);
                legacy.update("""
                    INSERT INTO account_emails(id, email, verified, last_requested_at)
                    VALUES (?, ?, true, now()), (?, ?, false, now())
                    """, userId, email, waiting, pendingEmail);
                legacy.update("""
                    INSERT INTO account_tokens(id, user_id, token_hash, purpose, destination, expires_at)
                    VALUES (?, ?, 'legacy_hash', 'VERIFY_EMAIL', ?, now() + interval '30 minutes')
                    """, token, waiting, pendingEmail);
                legacy.update("""
                    INSERT INTO email_outbox(id, user_id, recipient, subject, body, deduplication_key, next_attempt_at, created_at)
                    VALUES (?, ?, ?, 'Verify', 'legacy-link', ?, now(), now())
                    """, message, waiting, pendingEmail, "account:" + token);
                config.target("latest").load().migrate();

                var migrated = new AccountEmailService(legacy, Clock.systemUTC(), "http://localhost");
                assertEquals(new AccountEmailService.EmailStatus(email, true, null), migrated.status(userId));
                assertEquals(new AccountEmailService.EmailStatus(pendingEmail, false, pendingEmail), migrated.status(waiting));
                assertNull(legacy.queryForObject("SELECT email FROM account_emails WHERE id = ?", String.class, waiting));
                assertEquals(1, legacy.queryForObject("""
                    SELECT count(*) FROM email_outbox o JOIN account_tokens t ON o.reference_id = t.id
                    WHERE o.id = ? AND t.id = ? AND o.body = 'legacy-link' AND o.expires_at = t.expires_at
                    """, Integer.class, message, token));
            } finally {
                connection.setSchema("public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private Callable<Integer> claimWhenReady(CountDownLatch start, UUID accountId) {
        return () -> {
            start.await();
            try {
                service.requestVerification(accountId, email);
                return 200;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };
    }

    private UUID otherUser() {
        return users.save(new User("other-" + UUID.randomUUID().toString().substring(0, 8),
            "unused", "Other", "Test", UserRole.CUSTOMER)).getId();
    }

    @Test
    void publicVerificationEndpointValidatesTokensAndAccountRequiresLogin() throws Exception {
        mvc.perform(post("/api/account/email").contentType("application/json")
            .content("{\"email\":\"test@example.test\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/verify-email").contentType("application/json")
            .content("{\"token\":\"invalid\"}")).andExpect(status().isBadRequest());
        service.requestVerification(userId, email);
        mvc.perform(post("/api/auth/verify-email").contentType("application/json")
            .content("{\"token\":\"" + latestToken() + "\"}")).andExpect(status().isOk());
    }

    private String latestToken() {
        String body = jdbc.queryForObject(
            "SELECT body FROM email_outbox WHERE user_id = ? ORDER BY created_at DESC LIMIT 1", String.class, userId);
        return body.substring(body.indexOf("#token=") + 7);
    }
}
