package com.slotly.api.module.booking.service;

import com.slotly.api.module.booking.entity.Appointment;
import com.slotly.api.module.booking.entity.AppointmentStatus;
import com.slotly.api.module.booking.repository.AppointmentRepository;
import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.waitlist.entity.WaitlistEntry;
import com.slotly.api.module.waitlist.service.WaitlistQueue;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.slotly.api.common.exception.ApiException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AppointmentReservation {
    private final AppointmentRepository appointments;
    private final AvailabilityService availability;
    private final BookingCapacityService capacity;
    private final WaitlistQueue queue;
    private final Clock clock;

    public AppointmentReservation(
        AppointmentRepository appointments,
        AvailabilityService availability,
        BookingCapacityService capacity,
        WaitlistQueue queue,
        Clock clock
    ) {
        this.appointments = appointments;
        this.availability = availability;
        this.capacity = capacity;
        this.queue = queue;
        this.clock = clock;
    }

    // Both callers own the place lock until the appointment and any offer transitions commit.
    public Appointment reserve(User customer, Place place, Offering offering, Instant startsAt, WaitlistEntry offer) {
        availability.ensureBookableOffering(place, offering);
        Instant endsAt = startsAt.plus(Duration.ofMinutes(offering.getDurationMinutes()));
        if (!startsAt.isAfter(clock.instant()) || !availability.isValidServiceSlot(place.getId(), offering, startsAt, endsAt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Appointment must start at an available service time");
        }
        UUID heldSeat = null;
        if (offer == null) {
            queue.advanceBeforeBooking(place, startsAt, endsAt);
        } else {
            if (!endsAt.equals(offer.getRequestedEndsAt())) {
                throw new ApiException(HttpStatus.CONFLICT, "The offered service duration has changed");
            }
            heldSeat = offer.getId();
        }
        if (appointments.existsByCustomerIdAndOfferingIdAndStartsAtAndStatus(
            customer.getId(), offering.getId(), startsAt, AppointmentStatus.CONFIRMED
        )) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have this appointment");
        }
        if (!capacity.hasCapacity(place, startsAt, endsAt, heldSeat)) {
            throw new ApiException(HttpStatus.CONFLICT, "Place has no capacity for this time");
        }
        return appointments.save(new Appointment(customer, place, offering, startsAt, endsAt));
    }
}
