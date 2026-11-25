package com.slotly.api.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

public class ApiException extends ResponseStatusException {
    private final String code;

    public ApiException(HttpStatus status, String message) {
        this(status, codeFor(status), message, null);
    }

    protected ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(Objects.requireNonNull(status), message, cause);
        this.code = Objects.requireNonNull(code);
    }

    public HttpStatus getStatus() {
        return HttpStatus.valueOf(getStatusCode().value());
    }

    public String getCode() {
        return code;
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
