package com.slotly.api.dto.response;

import com.slotly.api.entity.WaitlistEntry;
import java.time.Instant;
import java.util.UUID;

public record WaitlistResponse(
    UUID id,
    UUID placeId,
    String placeName,
    UUID offeringId,
    String offeringName,
    Instant startsAt,
    Instant endsAt,
    String status,
    Instant offerExpiresAt
) {
    public static WaitlistResponse from(WaitlistEntry entry) {
        return new WaitlistResponse(
            entry.getId(),
            entry.getPlace().getId(),
            entry.getPlace().getName(),
            entry.getOffering().getId(),
            entry.getOffering().getName(),
            entry.getRequestedStartsAt(),
            entry.getRequestedEndsAt(),
            entry.getStatus().name(),
            entry.getOfferExpiresAt()
        );
    }
}
