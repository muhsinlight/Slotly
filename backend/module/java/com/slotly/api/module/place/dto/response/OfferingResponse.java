package com.slotly.api.module.place.dto.response;

import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.OfferingKind;

import java.math.BigDecimal;
import java.util.UUID;

public record OfferingResponse(
    UUID id,
    UUID placeId,
    String name,
    String description,
    BigDecimal price,
    Integer durationMinutes,
    OfferingKind kind,
    boolean active
) {
    public static OfferingResponse from(Offering offering) {
        return new OfferingResponse(
            offering.getId(),
            offering.getPlace().getId(),
            offering.getName(),
            offering.getDescription(),
            offering.getPrice(),
            offering.getDurationMinutes(),
            offering.getKind(),
            offering.isActive()
        );
    }
}
