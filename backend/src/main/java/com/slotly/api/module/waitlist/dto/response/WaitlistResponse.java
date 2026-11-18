package com.slotly.api.module.waitlist.dto.response;

import com.slotly.api.module.waitlist.entity.WaitlistEntry;
import com.slotly.api.module.waitlist.entity.WaitlistStatus;
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
        return from(entry, entry.getStatus());
    }

    public static WaitlistResponse at(WaitlistEntry entry, Instant now) {
        return from(entry, entry.isDue(now) ? WaitlistStatus.EXPIRED : entry.getStatus());
    }

    private static WaitlistResponse from(WaitlistEntry entry, WaitlistStatus status) {
        return new WaitlistResponse(
            entry.getId(),
            entry.getPlace().getId(),
            entry.getPlace().getName(),
            entry.getOffering().getId(),
            entry.getOffering().getName(),
            entry.getRequestedStartsAt(),
            entry.getRequestedEndsAt(),
            status.name(),
            entry.getOfferExpiresAt()
        );
    }
}
