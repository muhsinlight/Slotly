package com.slotly.api;

import com.slotly.api.module.auth.service.AccountEmailService;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.entity.UserRole;
import com.slotly.api.module.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
        String token = latestToken();
        service.verify(token);
        assertTrue(service.status(userId).verified());
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
    void expiredTokensAndDuplicateAddressesAreRejected() {
        service.requestVerification(userId, email);
        String token = latestToken();
        jdbc.update("UPDATE account_tokens SET expires_at = now() - interval '1 second' WHERE user_id = ?", userId);
        assertThrows(ResponseStatusException.class, () -> service.verify(token));
        UUID other = users.save(new User("other-" + UUID.randomUUID().toString().substring(0, 8),
            "unused", "Other", "Test", UserRole.CUSTOMER)).getId();
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> service.requestVerification(other, email)).getStatusCode().value());
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
