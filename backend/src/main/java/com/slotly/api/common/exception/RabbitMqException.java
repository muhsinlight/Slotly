package com.slotly.api.common.exception;

import org.springframework.http.HttpStatus;

public final class RabbitMqException extends ApiException {
    public RabbitMqException(Throwable cause) {
        super(
                HttpStatus.SERVICE_UNAVAILABLE,
                "RABBITMQ_ERROR",
                "The messaging service is temporarily unavailable",
                cause
        );
    }
}
