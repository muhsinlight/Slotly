package com.slotly.api.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Objects;

public class ApiException extends BaseApiException {
    public ApiException(HttpStatus status, String message) {
        this(status, codeFor(status), message, null);
    }

    protected ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(status, code, message, cause);
    }

    private static String codeFor(HttpStatus status) {
        return switch (Objects.requireNonNull(status)) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case GONE -> "GONE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_ERROR";
        };
    }
}
