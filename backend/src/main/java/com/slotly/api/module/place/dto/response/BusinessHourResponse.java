package com.slotly.api.module.place.dto.response;

import com.slotly.api.module.place.entity.BusinessHour;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record BusinessHourResponse(
    UUID id,
    DayOfWeek dayOfWeek,
    LocalTime opensAt,
    LocalTime closesAt
) {
    public static BusinessHourResponse from(BusinessHour hour) {
        return new BusinessHourResponse(
            hour.getId(),
            hour.getDayOfWeek(),
            hour.getOpensAt(),
            hour.getClosesAt()
        );
    }
}
