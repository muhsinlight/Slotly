package com.slotly.api.module.notification.service;

import java.util.UUID;

/** An authorized provider adapter must honor messageId as its idempotency key. */
public interface EmailGateway {
    void send(UUID messageId, String recipient, String subject, String body);
}
