package com.slotly.api.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
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
public class WaitlistNotificationServiceImpl implements com.slotly.api.service.WaitlistNotificationService {
    private final Map<UUID, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID customerId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        subscribers.compute(customerId, (id, existing) -> {
            Set<SseEmitter> emitters = existing == null ? new CopyOnWriteArraySet<>() : existing;
            emitters.add(emitter);
            return emitters;
        });
        emitter.onCompletion(() -> remove(customerId, emitter));
        emitter.onTimeout(() -> {
            remove(customerId, emitter);
            emitter.complete();
        });
        emitter.onError(ignored -> remove(customerId, emitter));
        send(customerId, emitter, SseEmitter.event().name("connected").data("ready"));
        return emitter;
    }

    @Scheduled(fixedDelay = 20_000)
    public void heartbeat() {
        subscribers.forEach((customerId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                send(customerId, emitter, SseEmitter.event().comment("keepalive"));
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishOffer(WaitlistOfferEvent event) {
        Set<SseEmitter> emitters = subscribers.get(event.customerId());
        if (emitters == null) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            send(event.customerId(), emitter, SseEmitter.event()
                    .name("offer")
                    .data(Map.of(
                        "entryId", event.entryId(),
                        "offerExpiresAt", event.offerExpiresAt()
                    )));
        }
    }

    private void send(UUID customerId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            remove(customerId, emitter);
            emitter.complete();
        }
    }

    private void remove(UUID customerId, SseEmitter emitter) {
        subscribers.computeIfPresent(customerId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
