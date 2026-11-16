package com.slotly.api.module.booking.service;

import com.slotly.api.module.booking.entity.Appointment;
import com.slotly.api.module.booking.entity.AppointmentStatus;
import com.slotly.api.module.booking.repository.AppointmentRepository;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.waitlist.entity.WaitlistEntry;
import com.slotly.api.module.waitlist.entity.WaitlistStatus;
import com.slotly.api.module.waitlist.repository.WaitlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class BookingCapacityService {
    private final AppointmentRepository appointments;
    private final WaitlistRepository waitlist;
    private final Clock clock;

    public BookingCapacityService(AppointmentRepository appointments, WaitlistRepository waitlist, Clock clock) {
        this.appointments = appointments;
        this.waitlist = waitlist;
        this.clock = clock;
    }

    public void validateCapacityReduction(UUID placeId, int capacity) {
        Instant now = clock.instant();
        TreeMap<Instant, Integer> changes = new TreeMap<>();
        for (Appointment appointment : appointments.findByPlaceIdAndStatusAndEndsAtGreaterThan(
            placeId, AppointmentStatus.CONFIRMED, now
        )) {
            changes.merge(appointment.getStartsAt().isBefore(now) ? now : appointment.getStartsAt(), 1, Integer::sum);
            changes.merge(appointment.getEndsAt(), -1, Integer::sum);
        }
        for (WaitlistEntry offer : waitlist.findByPlaceIdAndStatusAndOfferExpiresAtGreaterThan(
            placeId, WaitlistStatus.OFFERED, now
        )) {
            if (offer.getRequestedEndsAt().isAfter(now)) {
                changes.merge(offer.getRequestedStartsAt().isBefore(now) ? now : offer.getRequestedStartsAt(), 1, Integer::sum);
                changes.merge(offer.getRequestedEndsAt(), -1, Integer::sum);
            }
        }
        int active = 0;
        for (int change : changes.values()) {
            active += change;
            if (active > capacity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Kapasite mevcut randevu veya aktif bekleme tekliflerinin altına indirilemez.");
            }
        }
    }

    public boolean hasCapacity(Place place, Instant startsAt, Instant endsAt) {
        return hasCapacity(place, startsAt, endsAt, null);
    }

    public boolean hasCapacity(Place place, Instant startsAt, Instant endsAt, UUID acceptedOfferId) {
        List<Appointment> overlapping = appointments.findByPlaceIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            place.getId(),
            AppointmentStatus.CONFIRMED,
            endsAt,
            startsAt
        );
        TreeMap<Instant, Integer> changes = new TreeMap<>();
        for (Appointment appointment : overlapping) {
            Instant start = appointment.getStartsAt().isBefore(startsAt) ? startsAt : appointment.getStartsAt();
            Instant end = appointment.getEndsAt().isAfter(endsAt) ? endsAt : appointment.getEndsAt();
            changes.merge(start, 1, Integer::sum);
            changes.merge(end, -1, Integer::sum);
        }
        List<WaitlistEntry> holds = waitlist
            .findByPlaceIdAndStatusAndOfferExpiresAtGreaterThanAndRequestedStartsAtLessThanAndRequestedEndsAtGreaterThan(
                place.getId(), WaitlistStatus.OFFERED, Instant.now(clock), endsAt, startsAt
            );
        for (WaitlistEntry hold : holds) {
            if (hold.getId().equals(acceptedOfferId)) {
                continue;
            }
            Instant start = hold.getRequestedStartsAt().isBefore(startsAt) ? startsAt : hold.getRequestedStartsAt();
            Instant end = hold.getRequestedEndsAt().isAfter(endsAt) ? endsAt : hold.getRequestedEndsAt();
            changes.merge(start, 1, Integer::sum);
            changes.merge(end, -1, Integer::sum);
        }
        int active = 0;
        for (int change : changes.values()) {
            active += change;
            if (active >= place.getCapacity()) {
                return false;
            }
        }
        return true;
    }
}
