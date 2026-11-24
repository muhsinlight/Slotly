package com.slotly.api.module.waitlist.dto.response;

import com.slotly.api.module.waitlist.entity.WaitlistEntry;
import com.slotly.api.module.waitlist.entity.WaitlistStatus;
import java.time.Instant;
import java.time.ZoneId;
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
    Instant offerExpiresAt,
    String timeZone
) {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

    public static WaitlistResponse from(WaitlistEntry entry) {
        return from(entry, entry.getStatus(), DEFAULT_ZONE);
    }

    public static WaitlistResponse from(WaitlistEntry entry, ZoneId timeZone) {
        return from(entry, entry.getStatus(), timeZone);
    }

    public static WaitlistResponse at(WaitlistEntry entry, Instant now) {
        return at(entry, now, DEFAULT_ZONE);
    }

    public static WaitlistResponse at(WaitlistEntry entry, Instant now, ZoneId timeZone) {
        return from(entry, entry.isDue(now) ? WaitlistStatus.EXPIRED : entry.getStatus(), timeZone);
    }

    private static WaitlistResponse from(WaitlistEntry entry, WaitlistStatus status, ZoneId timeZone) {
        return new WaitlistResponse(
            entry.getId(),
            entry.getPlace().getId(),
            entry.getPlace().getName(),
            entry.getOffering().getId(),
            entry.getOffering().getName(),
            entry.getRequestedStartsAt(),
            entry.getRequestedEndsAt(),
            status.name(),
            entry.getOfferExpiresAt(),
            timeZone.getId()
        );
    }
}
