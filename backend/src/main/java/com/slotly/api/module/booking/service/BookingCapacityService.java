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
import java.util.NavigableMap;
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
        return schedule(place, startsAt, endsAt, acceptedOfferId).hasCapacity(startsAt, endsAt);
    }

    public CapacitySchedule schedule(Place place, Instant from, Instant to, UUID acceptedOfferId) {
        var overlapping = appointments.findByPlaceIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            place.getId(),
            AppointmentStatus.CONFIRMED,
            to,
            from
        );
        TreeMap<Instant, Integer> changes = new TreeMap<>();
        for (Appointment appointment : overlapping) {
            changes.merge(appointment.getStartsAt(), 1, Integer::sum);
            changes.merge(appointment.getEndsAt(), -1, Integer::sum);
        }
        var holds = waitlist
            .findByPlaceIdAndStatusAndOfferExpiresAtGreaterThanAndRequestedStartsAtLessThanAndRequestedEndsAtGreaterThan(
                place.getId(), WaitlistStatus.OFFERED, clock.instant(), to, from
            );
        for (WaitlistEntry hold : holds) {
            if (hold.getId().equals(acceptedOfferId)) {
                continue;
            }
            changes.merge(hold.getRequestedStartsAt(), 1, Integer::sum);
            changes.merge(hold.getRequestedEndsAt(), -1, Integer::sum);
        }
        TreeMap<Instant, Instant> fullPeriods = new TreeMap<>();
        Instant fullSince = null;
        int active = 0;
        for (var change : changes.entrySet()) {
            active += change.getValue();
            if (active >= place.getCapacity() && fullSince == null) {
                fullSince = change.getKey();
            } else if (active < place.getCapacity() && fullSince != null) {
                fullPeriods.put(fullSince, change.getKey());
                fullSince = null;
            }
        }
        return new CapacitySchedule(fullPeriods);
    }

    public static final class CapacitySchedule {
        private final NavigableMap<Instant, Instant> fullPeriods;

        private CapacitySchedule(NavigableMap<Instant, Instant> fullPeriods) {
            this.fullPeriods = fullPeriods;
        }

        public boolean hasCapacity(Instant startsAt, Instant endsAt) {
            var lastFullPeriod = fullPeriods.lowerEntry(endsAt);
            return lastFullPeriod == null || !lastFullPeriod.getValue().isAfter(startsAt);
        }
    }
}
