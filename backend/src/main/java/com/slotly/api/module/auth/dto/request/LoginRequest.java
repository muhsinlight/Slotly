package com.slotly.api.module.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank
    String username,

    @NotBlank
    String password
) {}
