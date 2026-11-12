package com.slotly.api.service;

import java.time.Instant;
import java.util.UUID;

public record WaitlistOfferEvent(UUID customerId, UUID entryId, Instant offerExpiresAt) {
}
