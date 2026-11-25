package com.slotly.api.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

public abstract class BaseApiException extends ResponseStatusException {
    private final HttpStatus status;
    private final String code;

    protected BaseApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(Objects.requireNonNull(status), message, cause);
        this.status = status;
        this.code = Objects.requireNonNull(code);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
