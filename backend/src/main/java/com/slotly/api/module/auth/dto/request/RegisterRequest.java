package com.slotly.api.module.auth.dto.request;

import com.slotly.api.module.auth.validation.AccountPassword;
import com.slotly.api.module.user.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank
    @Size(max = 40)
    String username,

    @AccountPassword
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
