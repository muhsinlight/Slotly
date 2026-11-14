package com.slotly.api.module.auth.dto.response;

import com.slotly.api.module.user.entity.UserRole;
import java.util.UUID;

public record RegistrationResponse(
    UUID id,
    String username,
    UserRole role
) {}
