package com.slotly.api.module.auth.dto.response;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {}
