package com.slotly.api;

import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("integration-test")
@Transactional
class EntityAuditIntegrationTests {
    @Autowired
    private EntityManager entityManager;

    @Test
    void tracksCreationAndUpdateTimestamps() {
        User user = new User(
                "audit-test-" + System.nanoTime(),
                "password-hash",
                "Audit",
                "Test",
                UserRole.CUSTOMER
        );
        entityManager.persist(user);
        entityManager.flush();

        var createdAt = user.getCreatedAt();
        assertNotNull(createdAt);
        assertNotNull(user.getUpdatedAt());
        assertEquals(createdAt, user.getUpdatedAt());

        user.setFirstName("Updated");
        entityManager.flush();

        assertEquals(createdAt, user.getCreatedAt());
        assertTrue(user.getUpdatedAt().isAfter(createdAt));
    }
}
