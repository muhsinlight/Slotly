package com.slotly.api.module.waitlist.service;

import com.slotly.api.module.waitlist.service.impl.WaitlistOfferEvent;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface WaitlistNotificationService {
    SseEmitter subscribe(UUID customerId);
    void publishOffer(WaitlistOfferEvent event);
}
