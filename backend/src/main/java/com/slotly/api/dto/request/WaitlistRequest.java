package com.slotly.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record WaitlistRequest(
    @NotNull
    UUID placeId,
    @NotNull
    UUID offeringId,
    @NotNull
    Instant startsAt
) {
}
