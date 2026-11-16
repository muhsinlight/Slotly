package com.slotly.api.module.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailDeliveryScheduler {
    private final EmailDispatcher dispatcher;
    private final boolean enabled;

    public EmailDeliveryScheduler(EmailDispatcher dispatcher, @Value("${slotly.email.enabled:false}") boolean enabled) {
        this.dispatcher = dispatcher;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelay = 30_000)
    public void deliver() {
        if (!enabled) {
            return;
        }
        for (int count = 0; count < 20 && dispatcher.deliverNext(); count++) {
            // Each message is processed in its own transaction by the proxied dispatcher.
        }
    }
}
