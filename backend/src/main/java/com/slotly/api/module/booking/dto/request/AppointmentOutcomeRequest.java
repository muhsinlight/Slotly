package com.slotly.api.module.booking.dto.request;

import com.slotly.api.module.booking.entity.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record AppointmentOutcomeRequest(
    @NotNull
    AppointmentStatus status
) {
}
