package com.slotly.api.module.booking.dto.response;

import com.slotly.api.module.booking.entity.Appointment;

import java.time.Instant;
import java.time.ZoneId;
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
    String status,
    String timeZone
) {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

    public static AppointmentResponse from(Appointment appointment) {
        return from(appointment, DEFAULT_ZONE);
    }

    public static AppointmentResponse from(Appointment appointment, ZoneId timeZone) {
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
            appointment.getStatus().name(),
            timeZone.getId()
        );
    }
}
