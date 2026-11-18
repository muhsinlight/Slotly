package com.slotly.api;

import com.slotly.api.module.auth.dto.request.LoginRequest;
import com.slotly.api.module.auth.service.AccountEmailService;
import com.slotly.api.module.auth.service.AuthService;
import com.slotly.api.module.auth.service.PasswordRecoveryService;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.entity.UserRole;
import com.slotly.api.module.user.repository.UserRepository;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class PasswordRecoveryIntegrationTests {
    @Autowired
    private PasswordRecoveryService recovery;
    @Autowired
    private AccountEmailService emails;
    @Autowired
    private AuthService auth;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mvc;

    private UUID userId;
    private String email;
    private String username;

    @BeforeEach
    void setup() {
        username = "reset-" + UUID.randomUUID().toString().substring(0, 8);
        userId = users.save(new User(username, passwords.encode("OldPassword2026!"),
            "Reset", "Test", UserRole.CUSTOMER)).getId();
        email = username + "@example.test";
        emails.requestVerification(userId, email);
    }

    @Test
    void onlyVerifiedAddressesReceiveRecoveryAndRepeatedRequestsAreSuppressed() {
        recovery.request(email);
        assertEquals(0, resetCount());
        jdbc.update("DELETE FROM password_reset_limits WHERE id = encode(sha256(?::bytea), 'hex')", email);
        emails.verify(latestToken("VERIFY_EMAIL"));
        recovery.request(email);
        recovery.request(email);
        recovery.request("unknown-" + email);
        assertEquals(1, resetCount());
    }

    @Test
    void resetIsSingleUseChangesPasswordAndRevokesExistingSessions() throws Exception {
        emails.verify(latestToken("VERIFY_EMAIL"));
        String session = auth.login(new LoginRequest(username, "OldPassword2026!")).accessToken();
        recovery.request(email);
        String reset = latestToken("RESET_PASSWORD");
        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
            .content("{\"token\":\"" + reset + "\",\"password\":\"NewPassword2026!\"}"))
            .andExpect(status().isOk());
        assertThrows(ResponseStatusException.class, () -> recovery.reset(reset, "AnotherPassword2026!"));
        assertThrows(ResponseStatusException.class, () -> auth.login(new LoginRequest(username, "OldPassword2026!")));
        assertNotNull(auth.login(new LoginRequest(username, "NewPassword2026!")));
        mvc.perform(get("/api/account/email").header("Authorization", "Bearer " + session))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokensPreventReset() {
        emails.verify(latestToken("VERIFY_EMAIL"));
        recovery.request(email);
        String token = latestToken("RESET_PASSWORD");
        jdbc.update("UPDATE account_tokens SET expires_at = now() - interval '1 second' WHERE user_id = ?", userId);
        assertThrows(ResponseStatusException.class, () -> recovery.reset(token, "NewPassword2026!"));
    }

    @Test
    void recoveryKeepsUsingTheVerifiedAddressUntilTheChangeIsConfirmed() {
        emails.verify(latestToken("VERIFY_EMAIL"));
        String replacement = "new-" + email;
        jdbc.update("UPDATE account_emails SET last_requested_at = now() - interval '2 minutes' WHERE id = ?", userId);
        emails.requestVerification(userId, replacement);
        assertEquals(new AccountEmailService.EmailStatus(email, true, replacement), emails.status(userId));
        recovery.request(replacement);
        assertEquals(0, resetCount());
        recovery.request(email);
        assertEquals(1, resetCount());

        String reset = latestToken("RESET_PASSWORD");
        emails.verify(latestToken("VERIFY_EMAIL"));
        assertEquals(new AccountEmailService.EmailStatus(replacement, true, null), emails.status(userId));
        assertThrows(ResponseStatusException.class, () -> recovery.reset(reset, "NewPassword2026!"));
    }

    @Test
    void registrationAndResetEnforceTheSameUtf8PasswordLimit() throws Exception {
        String boundary = "ş".repeat(36);
        String tooLong = boundary + "a";
        String registration = """
            {"username":"%s","password":"%s","firstName":"Api","lastName":"Test","role":"CUSTOMER"}
            """;
        mvc.perform(post("/api/auth/register").contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
            .content(registration.formatted("pw-" + username, tooLong)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data.password").value("Parola UTF-8 kodlamasında en fazla 72 bayt olabilir."));
        mvc.perform(post("/api/auth/register").contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
            .content(registration.formatted("pw-" + username, boundary)))
            .andExpect(status().isCreated());

        emails.verify(latestToken("VERIFY_EMAIL"));
        recovery.request(email);
        String reset = latestToken("RESET_PASSWORD");
        String body = """
            {"token":"%s","password":"%s"}
            """;
        mvc.perform(post("/api/auth/reset-password").contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
            .content(body.formatted(reset, tooLong)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data.password").value("Parola UTF-8 kodlamasında en fazla 72 bayt olabilir."));
        mvc.perform(post("/api/auth/reset-password").contentType("application/json").characterEncoding(StandardCharsets.UTF_8)
            .content(body.formatted(reset, boundary)))
            .andExpect(status().isOk());
        assertNotNull(auth.login(new LoginRequest(username, boundary)));
    }

    @Test
    void anonymousRequestHasSameResponseForUnknownAndUnverifiedEmail() throws Exception {
        for (String address : new String[] { email, "unknown-" + email }) {
            mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"" + address + "\"}"))
                .andExpect(status().isOk());
        }
        assertEquals(0, resetCount());
    }

    private int resetCount() {
        return jdbc.queryForObject("SELECT count(*) FROM account_tokens WHERE user_id = ? AND purpose = 'RESET_PASSWORD'",
            Integer.class, userId);
    }

    private String latestToken(String purpose) {
        String body = jdbc.queryForObject("""
            SELECT o.body FROM email_outbox o JOIN account_tokens t ON o.deduplication_key = 'account:' || t.id
            WHERE t.user_id = ? AND t.purpose = ? ORDER BY o.created_at DESC LIMIT 1
            """, String.class, userId, purpose);
        return body.substring(body.indexOf("#token=") + 7);
    }
}
