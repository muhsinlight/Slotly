package com.slotly.api.service.impl;

import java.time.Instant;
import java.util.UUID;

public record WaitlistOfferEvent(UUID customerId, UUID entryId, Instant offerExpiresAt) {
}
