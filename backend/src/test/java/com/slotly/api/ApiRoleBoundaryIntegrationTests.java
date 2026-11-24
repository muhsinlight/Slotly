package com.slotly.api;

import com.slotly.api.module.auth.dto.request.LoginRequest;
import com.slotly.api.module.auth.dto.request.RegisterRequest;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.entity.UserRole;
import com.slotly.api.module.user.repository.UserRepository;
import com.slotly.api.module.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ApiRoleBoundaryIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private AuthService auth;

    @Test
    void anonymousRequestsCannotReadPrivateCalendars() throws Exception {
        mockMvc.perform(get("/api/owner/places")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/appointments/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreateOwnerResourcesAndOwnerCannotBookAsCustomer() throws Exception {
        String customer = tokenFor(UserRole.CUSTOMER);
        String owner = tokenFor(UserRole.OWNER);
        mockMvc.perform(post("/api/owner/places")
                .header("Authorization", "Bearer " + customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/appointments")
                .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/waitlist/me").header("Authorization", "Bearer " + owner))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminOverviewIsPrivateAndPublicRegistrationCannotCreateAdmin() throws Exception {
        String customer = tokenFor(UserRole.CUSTOMER);
        String admin = tokenFor(UserRole.ADMIN);

        mockMvc.perform(get("/api/admin/overview")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + customer))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> auth.register(
            new RegisterRequest("admin-public", "AdminTest2026!", "Admin", "Test", UserRole.ADMIN)
        ));
    }

    @Test
    void outcomePreflightAllowsPatchFromFrontend() throws Exception {
        mockMvc.perform(options("/api/owner/appointments/{id}/outcome", UUID.randomUUID())
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PATCH"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("PATCH")));
    }

    @Test
    void invalidAndMissingQueryParametersReturnBadRequest() throws Exception {
        String owner = tokenFor(UserRole.OWNER);
        String authorization = "Bearer " + owner;

        mockMvc.perform(get("/api/owner/appointments")
                .header("Authorization", authorization)
                .param("placeId", "not-a-uuid")
                .param("from", "2026-11-18T10:00:00Z")
                .param("to", "2026-11-19T10:00:00Z"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Invalid or missing request parameter"));

        mockMvc.perform(get("/api/owner/appointments")
                .header("Authorization", authorization)
                .param("from", "2026-11-18T10:00:00Z")
                .param("to", "2026-11-19T10:00:00Z"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Invalid or missing request parameter"));
    }

    @Test
    void unsupportedMethodsMediaTypesAndMissingRoutesDoNotReturnInternalServerError() throws Exception {
        String customer = tokenFor(UserRole.CUSTOMER);

        mockMvc.perform(delete("/api/places"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.status").value(405));

        mockMvc.perform(post("/api/places/{id}/offerings", UUID.randomUUID())
                .header("Authorization", "Bearer " + customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post("/api/places")
                .header("Authorization", "Bearer " + customer)
                .contentType(MediaType.TEXT_PLAIN)
                .content("plain-body"))
            .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post("/api/appointments")
                .header("Authorization", "Bearer " + customer)
                .contentType(MediaType.TEXT_PLAIN)
                .content("plain-body"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.status").value(415));

        mockMvc.perform(get("/api/places")
                .accept(MediaType.valueOf("text/csv")))
            .andExpect(status().isNotAcceptable());

        mockMvc.perform(get("/api/unknown-endpoint")
                .header("Authorization", "Bearer " + customer))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    private String tokenFor(UserRole role) {
        String username = "role-" + UUID.randomUUID().toString().substring(0, 8);
        String password = "RoleBoundaryTest2026!";
        users.save(new User(username, passwords.encode(password), "Role", "Test", role));
        return auth.login(new LoginRequest(username, password)).accessToken();
    }
}
