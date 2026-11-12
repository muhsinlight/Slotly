package com.slotly.api;

import com.slotly.api.dto.request.LoginRequest;
import com.slotly.api.entity.User;
import com.slotly.api.entity.UserRole;
import com.slotly.api.repository.UserRepository;
import com.slotly.api.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private String tokenFor(UserRole role) {
        String username = "role-" + UUID.randomUUID().toString().substring(0, 8);
        String password = "RoleBoundaryTest2026!";
        users.save(new User(username, passwords.encode(password), "Role", "Test", role));
        return auth.login(new LoginRequest(username, password)).accessToken();
    }
}
