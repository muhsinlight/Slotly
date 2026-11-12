package com.slotly.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class WaitlistNotificationService {
    private final Map<UUID, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID customerId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(customerId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> remove(customerId, emitter));
        emitter.onTimeout(() -> remove(customerId, emitter));
        emitter.onError(ignored -> remove(customerId, emitter));
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishOffer(WaitlistOfferEvent event) {
        Set<SseEmitter> emitters = subscribers.get(event.customerId());
        if (emitters == null) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("offer")
                    .data(Map.of(
                        "entryId", event.entryId(),
                        "offerExpiresAt", event.offerExpiresAt()
                    )));
            } catch (IOException | IllegalStateException exception) {
                remove(event.customerId(), emitter);
            }
        }
    }

    private void remove(UUID customerId, SseEmitter emitter) {
        Set<SseEmitter> emitters = subscribers.get(customerId);
        if (emitters == null) {
            return;
        }

        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscribers.remove(customerId, emitters);
        }
    }
}
