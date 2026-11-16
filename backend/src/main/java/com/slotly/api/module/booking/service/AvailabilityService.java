package com.slotly.api.module.booking.service;

import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.OfferingKind;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.place.repository.OfferingRepository;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.place.service.BusinessCalendarService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        BusinessCalendarService calendar,
        @Value("${slotly.business.time-zone:Europe/Istanbul}") String zone
    ) {
        this.places = places;
        this.offerings = offerings;
        this.capacity = capacity;
        this.clock = clock;
        this.calendar = calendar;
        this.zone = ZoneId.of(zone);
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
        long slotStepSeconds = offering.getDurationMinutes() * 60L;
        LocalDateTime rangeStart = from.atZone(zone).toLocalDate().atStartOfDay();
        LocalDateTime rangeEnd = to.atZone(zone).toLocalDate().atStartOfDay();

        for (LocalDateTime date = rangeStart; !date.isAfter(rangeEnd); date = date.plusDays(1)) {
            for (var businessHour : calendar.intervals(placeId, date.toLocalDate())) {

                Instant slot = date.toLocalDate().atTime(businessHour.opensAt()).atZone(zone).toInstant();
                while (slot.isBefore(from)) {
                    slot = slot.plusSeconds(slotStepSeconds);
                }
                Instant businessClosing = date.toLocalDate()
                    .atTime(businessHour.closesAt())
                    .atZone(zone)
                    .toInstant();

                while (!slot.plusSeconds(slotStepSeconds).isAfter(businessClosing)) {
                    Instant end = slot.plusSeconds(slotStepSeconds);
                    if (!slot.isBefore(Instant.now(clock)) && !end.isAfter(to) && capacity.hasCapacity(place, slot, end)) {
                        slots.add(slot);
                    }
                    slot = slot.plusSeconds(slotStepSeconds);
                }
            }
        }
        return slots;
    }

    public void ensureBookableOffering(Place place, Offering offering) {
        if (!place.isActive() || !offering.isActive() || offering.getKind() != OfferingKind.SERVICE
            || offering.getDurationMinutes() == null || !offering.getPlace().getId().equals(place.getId())) {
            throw bad("Offering is not bookable at this place");
        }
    }

    public boolean isValidServiceSlot(UUID placeId, Offering offering, Instant startsAt, Instant endsAt) {
        LocalDateTime localStart = startsAt.atZone(zone).toLocalDateTime();
        LocalDateTime localEnd = endsAt.atZone(zone).toLocalDateTime();
        return calendar.intervals(placeId, localStart.toLocalDate())
            .stream()
            .anyMatch(hour -> isWithinHour(hour, offering, localStart, localEnd));
    }

    private boolean isWithinHour(
        BusinessCalendarService.OpeningInterval hour,
        Offering offering,
        LocalDateTime startsAt,
        LocalDateTime endsAt
    ) {
        long offsetFromOpeningMinutes = Duration.between(hour.opensAt(), startsAt.toLocalTime()).toMinutes();
        return !startsAt.toLocalTime().isBefore(hour.opensAt())
            && !endsAt.toLocalTime().isAfter(hour.closesAt())
            && startsAt.toLocalDate().equals(endsAt.toLocalDate())
            && offsetFromOpeningMinutes % offering.getDurationMinutes() == 0;
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
