package com.slotly.api.dto.response;

import com.slotly.api.entity.Appointment;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
    UUID id,
    UUID customerId,
    String customerName,
    UUID placeId,
    String placeName,
    UUID offeringId,
    String offeringName,
    Instant startsAt,
    Instant endsAt,
    String status
) {
    public static AppointmentResponse from(Appointment appointment) {
        String customerName = appointment.getCustomer().getFirstName()
            + " "
            + appointment.getCustomer().getLastName();
        return new AppointmentResponse(
            appointment.getId(),
            appointment.getCustomer().getId(),
            customerName,
            appointment.getPlace().getId(),
            appointment.getPlace().getName(),
            appointment.getOffering().getId(),
            appointment.getOffering().getName(),
            appointment.getStartsAt(),
            appointment.getEndsAt(),
            appointment.getStatus().name()
        );
    }
}
