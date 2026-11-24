package com.slotly.api.module.booking.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AppointmentRescheduleRequest(@NotNull Instant startsAt) {
}
