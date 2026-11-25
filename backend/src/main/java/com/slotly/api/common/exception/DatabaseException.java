package com.slotly.api.common.exception;

import org.springframework.http.HttpStatus;

public final class DatabaseException extends ApiException {
    public DatabaseException(Throwable cause) {
        super(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "DATABASE_ERROR",
                "A database operation failed",
                cause
        );
    }
}
