package com.slotly.api;

import com.slotly.api.module.booking.entity.Appointment;
import com.slotly.api.module.booking.entity.AppointmentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppointmentOutcomeTests {
    private final Instant end = Instant.parse("2026-11-17T10:30:00Z");

    private Appointment appointment() {
        return new Appointment(null, null, null, end.minusSeconds(1800), end);
    }

    @Test
    void outcomeRequiresEndTimeAndAcceptsExactBoundary() {
        Appointment appointment = appointment();
        assertThrows(IllegalStateException.class, () ->
            appointment.recordOutcome(AppointmentStatus.COMPLETED, end.minusNanos(1)));
        assertEquals(AppointmentStatus.CONFIRMED, appointment.getStatus());
        appointment.recordOutcome(AppointmentStatus.COMPLETED, end);
        assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
    }

    @Test
    void bothOutcomesAreIdempotentAndCannotBeChangedOrCancelled() {
        for (AppointmentStatus outcome : new AppointmentStatus[] {
            AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW
        }) {
            Appointment appointment = appointment();
            appointment.recordOutcome(outcome, end);
            appointment.recordOutcome(outcome, end.plusSeconds(1));
            AppointmentStatus other = outcome == AppointmentStatus.COMPLETED
                ? AppointmentStatus.NO_SHOW : AppointmentStatus.COMPLETED;
            assertThrows(IllegalStateException.class, () -> appointment.recordOutcome(other, end));
            assertThrows(IllegalStateException.class, appointment::cancel);
            assertEquals(outcome, appointment.getStatus());
        }
    }

    @Test
    void cancellationIsTerminalAndInvalidTargetsDoNotMutateState() {
        Appointment appointment = appointment();
        assertThrows(IllegalArgumentException.class, () ->
            appointment.recordOutcome(AppointmentStatus.CANCELLED, end));
        assertThrows(IllegalArgumentException.class, () -> appointment.recordOutcome(null, end));
        assertEquals(AppointmentStatus.CONFIRMED, appointment.getStatus());
        appointment.cancel();
        assertThrows(IllegalStateException.class, () ->
            appointment.recordOutcome(AppointmentStatus.NO_SHOW, end));
        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
    }
}
