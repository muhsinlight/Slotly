package com.slotly.api.response;

import org.springframework.http.HttpStatus;

public final class ApiResponses {
    private ApiResponses() {
    }

    public static <T> ApiResponse<T> failure(HttpStatus status, String message) {
        return ApiResponse.<T>builder()
            .status(status)
            .message(message)
            .build();
    }
}
