package com.slotly.api.module.waitlist.service;

import com.slotly.api.module.booking.service.AvailabilityService;
import com.slotly.api.module.booking.service.BookingCapacityService;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.waitlist.entity.WaitlistEntry;
import com.slotly.api.module.waitlist.entity.WaitlistStatus;
import com.slotly.api.module.waitlist.repository.WaitlistRepository;
import com.slotly.api.module.waitlist.service.impl.WaitlistOfferEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class WaitlistQueue {
    public static final int EXPIRY_BATCH_SIZE = 100;
    private static final Duration OFFER_LIFETIME = Duration.ofMinutes(30);

    private final PlaceRepository places;
    private final WaitlistRepository waitlist;
    private final AvailabilityService availability;
    private final BookingCapacityService capacity;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public WaitlistQueue(
        PlaceRepository places,
        WaitlistRepository waitlist,
        AvailabilityService availability,
        BookingCapacityService capacity,
        Clock clock,
        ApplicationEventPublisher events
    ) {
        this.places = places;
        this.waitlist = waitlist;
        this.availability = availability;
        this.capacity = capacity;
        this.clock = clock;
        this.events = events;
    }

    // The caller holds the place lock through allocation and its own booking or update.
    public void fillReleasedWindow(Place place, Instant startsAt, Instant endsAt) {
        Instant now = clock.instant();
        if (place.isActive() && endsAt.isAfter(now)) {
            var candidates = waitlist.waitingInWindow(place.getId(), now, startsAt, endsAt);
            offerAvailable(place, candidates);
        }
    }

    public void fillAvailableCapacity(Place place) {
        offerAvailable(place, waitlist.waitingCandidates(place.getId(), clock.instant()));
    }

    public void advanceBeforeBooking(Place place, Instant startsAt, Instant endsAt) {
        Instant now = clock.instant();
        var expired = waitlist.expiredOffersInWindow(place.getId(), now, startsAt, endsAt,
            PageRequest.of(0, EXPIRY_BATCH_SIZE));
        var released = closeDue(expired, now);
        released.add(new Window(startsAt, endsAt));
        fillReleasedWindows(place, released);
    }

    @Transactional
    public void expireEntries(UUID placeId, List<UUID> entryIds) {
        Place place = places.findLockedById(placeId).orElseThrow();
        var released = closeDue(waitlist.findAllById(entryIds), clock.instant());
        fillReleasedWindows(place, released);
    }

    private Set<Window> closeDue(List<WaitlistEntry> entries, Instant now) {
        Set<Window> released = new LinkedHashSet<>();
        for (WaitlistEntry entry : entries) {
            if (!entry.isDue(now)) {
                continue;
            }
            if (entry.getStatus() == WaitlistStatus.OFFERED) {
                released.add(new Window(entry.getRequestedStartsAt(), entry.getRequestedEndsAt()));
            }
            entry.changeStatus(WaitlistStatus.EXPIRED);
        }
        return released;
    }

    private void fillReleasedWindows(Place place, Set<Window> released) {
        if (released.size() == 1) {
            Window window = released.iterator().next();
            fillReleasedWindow(place, window.start(), window.end());
            return;
        }
        if (!place.isActive() || released.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        var candidates = new TreeSet<>(Comparator.comparing(WaitlistEntry::getCreatedAt)
            .thenComparing(entry -> entry.getId().toString()));
        for (Window window : released) {
            if (window.end().isAfter(now)) {
                candidates.addAll(waitlist.waitingInWindow(place.getId(), now, window.start(), window.end()));
            }
        }
        offerAvailable(place, candidates);
    }

    private void offerAvailable(Place place, Collection<WaitlistEntry> candidates) {
        Set<Window> fullWindows = new LinkedHashSet<>();
        for (WaitlistEntry entry : candidates) {
            var offering = entry.getOffering();
            Instant start = entry.getRequestedStartsAt();
            Instant end = entry.getRequestedEndsAt();
            Window window = new Window(start, end);
            if (fullWindows.contains(window) || !availability.isBookableOffering(place, offering)
                || !availability.isValidServiceSlot(place.getId(), offering, start, end)) {
                continue;
            }
            if (!capacity.hasCapacity(place, start, end)) {
                fullWindows.add(window);
                continue;
            }
            Instant now = clock.instant();
            if (!start.isAfter(now)) {
                entry.changeStatus(WaitlistStatus.EXPIRED);
                continue;
            }
            Instant normalEnd = now.plus(OFFER_LIFETIME);
            Instant deadline = normalEnd.isBefore(start) ? normalEnd : start;
            entry.offer(now, deadline);
            events.publishEvent(new WaitlistOfferEvent(entry.getCustomer().getId(), entry.getId(), deadline));
        }
    }

    private record Window(Instant start, Instant end) {
    }
}
