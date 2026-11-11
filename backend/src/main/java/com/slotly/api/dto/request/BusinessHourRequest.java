package com.slotly.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record BusinessHourRequest(
    @NotNull
    DayOfWeek dayOfWeek,
    @NotNull
    LocalTime opensAt,
    @NotNull
    LocalTime closesAt
) {
}
