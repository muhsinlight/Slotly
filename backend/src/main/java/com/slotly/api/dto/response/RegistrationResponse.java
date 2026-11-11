package com.slotly.api.dto.response;

import com.slotly.api.entity.UserRole;
import java.util.UUID;

public record RegistrationResponse(
    UUID id,
    String username,
    UserRole role
) {}
