package com.slotly.api.module.booking.service;

import com.slotly.api.module.booking.dto.request.AppointmentRequest;
import com.slotly.api.module.booking.dto.response.AppointmentResponse;

import com.slotly.api.module.booking.entity.AppointmentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingService {
    AppointmentResponse createAppointment(UUID customerId, AppointmentRequest request);
    List<AppointmentResponse> myAppointments(UUID customerId);
    void cancel(UUID callerId, UUID appointmentId);
    AppointmentResponse reschedule(UUID customerId, UUID appointmentId, Instant startsAt);
    AppointmentResponse recordOutcome(UUID ownerId, UUID appointmentId, AppointmentStatus outcome);
    List<AppointmentResponse> ownerAppointments(UUID ownerId, UUID placeId, Instant from, Instant to);
}
