package com.slotly.api.module.booking.service;

import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.OfferingKind;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.place.repository.OfferingRepository;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.place.service.BusinessCalendarService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class AvailabilityService {
    private static final Duration MAX_AVAILABILITY_RANGE = Duration.ofDays(31);
    private final PlaceRepository places;
    private final OfferingRepository offerings;
    private final BookingCapacityService capacity;
    private final Clock clock;
    private final BusinessCalendarService calendar;
    private final ZoneId zone;

    public AvailabilityService(
        PlaceRepository places,
        OfferingRepository offerings,
        BookingCapacityService capacity,
        Clock clock,
        BusinessCalendarService calendar
    ) {
        this.places = places;
        this.offerings = offerings;
        this.capacity = capacity;
        this.clock = clock;
        this.calendar = calendar;
        this.zone = calendar.timeZone();
    }

    @Transactional(readOnly = true)
    public List<Instant> availability(UUID placeId, UUID offeringId, Instant from, Instant to) {
        Place place = requirePlace(placeId);
        if (!place.isActive()) {
            throw notFound("Place not found");
        }
        Offering offering = requireOffering(offeringId);
        ensureBookableOffering(place, offering);
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_AVAILABILITY_RANGE) > 0) {
            throw bad("Availability range must be positive and at most 31 days");
        }

        List<Instant> slots = new ArrayList<>();
        Duration step = Duration.ofMinutes(offering.getDurationMinutes());
        LocalDate firstDate = from.atZone(zone).toLocalDate();
        LocalDate lastDate = to.atZone(zone).toLocalDate();
        var hours = calendar.intervals(placeId, firstDate, lastDate);
        var schedule = capacity.schedule(place, from, to, null);
        Instant now = clock.instant();
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            slotStarts(date, hours.get(date), step)
                .filter(slot -> slot.isAfter(now) && !slot.isBefore(from) && !slot.plus(step).isAfter(to))
                .filter(slot -> schedule.hasCapacity(slot, slot.plus(step)))
                .forEach(slots::add);
        }
        return slots;
    }

    public void ensureBookableOffering(Place place, Offering offering) {
        if (!isBookableOffering(place, offering)) {
            throw bad("Offering is not bookable at this place");
        }
    }

    public boolean isBookableOffering(Place place, Offering offering) {
        return place.isActive() && offering.isActive() && offering.getKind() == OfferingKind.SERVICE
            && offering.getDurationMinutes() != null && offering.getDurationMinutes() > 0
            && offering.getPlace().getId().equals(place.getId());
    }

    public boolean isValidServiceSlot(UUID placeId, Offering offering, Instant startsAt, Instant endsAt) {
        LocalDate date = startsAt.atZone(zone).toLocalDate();
        Duration step = Duration.ofMinutes(offering.getDurationMinutes());
        return startsAt.plus(step).equals(endsAt)
            && slotStarts(date, calendar.intervals(placeId, date), step).anyMatch(startsAt::equals);
    }

    private Stream<Instant> slotStarts(
        LocalDate date,
        List<BusinessCalendarService.OpeningInterval> intervals,
        Duration step
    ) {
        return intervals.stream().flatMap(interval -> {
            Instant open = date.atTime(interval.opensAt()).atZone(zone).toInstant();
            Instant close = date.atTime(interval.closesAt()).atZone(zone).toInstant();
            return Stream.iterate(open, slot -> !slot.plus(step).isAfter(close), slot -> slot.plus(step));
        });
    }

    private Place requirePlace(UUID id) {
        return places.findById(id).orElseThrow(() -> notFound("Place not found"));
    }

    private Offering requireOffering(UUID id) {
        return offerings.findById(id).orElseThrow(() -> notFound("Offering not found"));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
