package com.slotly.api.module.place.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record CalendarExceptionRequest(
    @NotNull Boolean closed,
    LocalTime opensAt,
    LocalTime closesAt
) {
}
