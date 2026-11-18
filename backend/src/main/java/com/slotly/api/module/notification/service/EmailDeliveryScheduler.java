package com.slotly.api.module.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "slotly.rabbitmq.enabled", havingValue = "false", matchIfMissing = true)
public class EmailDeliveryScheduler {
    private final EmailDispatcher dispatcher;

    public EmailDeliveryScheduler(EmailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 30_000)
    public void deliver() {
        for (int count = 0; count < 20 && dispatcher.deliverNext(); count++) {
            // Each message is processed in its own transaction by the proxied dispatcher.
        }
    }
}
