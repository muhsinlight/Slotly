package com.slotly.api.dto.request;

import com.slotly.api.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank
    @Size(max = 40)
    String username,

    @NotBlank
    @Size(min = 10, max = 100)
    String password,

    @NotBlank
    @Size(max = 80)
    String firstName,

    @NotBlank
    @Size(max = 80)
    String lastName,

    @NotNull
    UserRole role
) {}
