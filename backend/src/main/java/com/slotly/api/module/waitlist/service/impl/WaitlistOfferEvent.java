package com.slotly.api.module.waitlist.service.impl;

import java.time.Instant;
import java.util.UUID;

public record WaitlistOfferEvent(UUID customerId, UUID entryId, Instant offerExpiresAt) {
}
