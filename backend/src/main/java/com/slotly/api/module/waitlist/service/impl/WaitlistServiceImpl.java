package com.slotly.api.module.waitlist.service.impl;

import com.slotly.api.module.booking.dto.response.AppointmentResponse;
import com.slotly.api.module.booking.entity.Appointment;
import com.slotly.api.module.booking.service.AppointmentReservation;
import com.slotly.api.module.booking.service.AvailabilityService;
import com.slotly.api.module.booking.service.BookingCapacityService;
import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.place.repository.OfferingRepository;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.repository.UserRepository;
import com.slotly.api.module.waitlist.dto.request.WaitlistRequest;
import com.slotly.api.module.waitlist.dto.response.WaitlistResponse;
import com.slotly.api.module.waitlist.entity.WaitlistEntry;
import com.slotly.api.module.waitlist.entity.WaitlistStatus;
import com.slotly.api.module.waitlist.repository.WaitlistRepository;
import com.slotly.api.module.waitlist.service.WaitlistService;
import com.slotly.api.module.waitlist.service.WaitlistQueue;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WaitlistServiceImpl implements WaitlistService {
    @PersistenceContext
    private EntityManager entityManager;

    private final PlaceRepository places;
    private final OfferingRepository offerings;
    private final AppointmentReservation reservations;
    private final UserRepository users;
    private final WaitlistRepository waitlist;
    private final BookingCapacityService capacity;
    private final AvailabilityService availability;
    private final Clock clock;
    private final WaitlistQueue queue;

    public WaitlistServiceImpl(
        PlaceRepository places,
        OfferingRepository offerings,
        AppointmentReservation reservations,
        UserRepository users,
        WaitlistRepository waitlist,
        BookingCapacityService capacity,
        AvailabilityService availability,
        Clock clock,
        WaitlistQueue queue
    ) {
        this.places = places;
        this.offerings = offerings;
        this.reservations = reservations;
        this.users = users;
        this.waitlist = waitlist;
        this.capacity = capacity;
        this.availability = availability;
        this.clock = clock;
        this.queue = queue;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public WaitlistResponse joinWaitlist(UUID customerId, WaitlistRequest request) {
        Place place = places.findLockedById(request.placeId()).orElseThrow(() -> notFound("Place not found"));
        Offering offering = requireOffering(request.offeringId());
        availability.ensureBookableOffering(place, offering);

        Instant startsAt = request.startsAt();
        Instant endsAt = startsAt.plusSeconds(offering.getDurationMinutes() * 60L);
        if (!startsAt.isAfter(Instant.now(clock))) {
            throw bad("Requested time must be in the future");
        }
        if (!availability.isValidServiceSlot(place.getId(), offering, startsAt, endsAt)) {
            throw bad("Requested time is outside business hours");
        }
        queue.advanceBeforeBooking(place, startsAt, endsAt);
        if (capacity.hasCapacity(place, startsAt, endsAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The requested time is currently available");
        }
        if (waitlist.existsByCustomerIdAndPlaceIdAndOfferingIdAndRequestedStartsAtAndStatusIn(
            customerId,
            place.getId(),
            offering.getId(),
            startsAt,
            List.of(WaitlistStatus.WAITING, WaitlistStatus.OFFERED)
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already waiting for this time");
        }

        User customer = users.findById(customerId).orElseThrow(() -> notFound("Customer not found"));
        WaitlistEntry entry = waitlist.save(new WaitlistEntry(customer, place, offering, startsAt, endsAt));
        return WaitlistResponse.from(entry);
    }

    @Transactional(readOnly = true)
    public List<WaitlistResponse> myWaitlist(UUID customerId) {
        Instant now = clock.instant();
        return waitlist.findByCustomerIdOrderByCreatedAtDesc(customerId)
            .stream()
            .map(entry -> WaitlistResponse.at(entry, now))
            .toList();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AppointmentResponse acceptWaitlistOffer(UUID customerId, UUID entryId) {
        WaitlistEntry entry = lockedEntry(customerId, entryId);
        Place place = entry.getPlace();
        ensureOpen(entry);
        if (entry.getStatus() != WaitlistStatus.OFFERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no active offer for this entry");
        }
        Appointment appointment;
        try {
            appointment = reservations.reserve(entry.getCustomer(), place, entry.getOffering(),
                entry.getRequestedStartsAt(), entry);
        } catch (ResponseStatusException exception) {
            entry.changeStatus(WaitlistStatus.EXPIRED);
            queue.fillReleasedWindow(place, entry.getRequestedStartsAt(), entry.getRequestedEndsAt());
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getReason());
        }
        entry.changeStatus(WaitlistStatus.ACCEPTED);
        return AppointmentResponse.from(appointment);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public WaitlistResponse declineWaitlistOffer(UUID customerId, UUID entryId) {
        WaitlistEntry entry = lockedEntry(customerId, entryId);
        ensureOpen(entry);
        if (entry.getStatus() != WaitlistStatus.OFFERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no active offer for this entry");
        }
        entry.changeStatus(WaitlistStatus.DECLINED);
        queue.fillReleasedWindow(entry.getPlace(), entry.getRequestedStartsAt(), entry.getRequestedEndsAt());
        return WaitlistResponse.from(entry);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void leaveWaitlist(UUID customerId, UUID entryId) {
        WaitlistEntry entry = lockedEntry(customerId, entryId);
        ensureOpen(entry);
        boolean hadOffer = entry.getStatus() == WaitlistStatus.OFFERED;
        if (entry.getStatus() != WaitlistStatus.WAITING && !hadOffer) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This waitlist entry is already closed");
        }
        entry.changeStatus(WaitlistStatus.LEFT);
        if (hadOffer) {
            queue.fillReleasedWindow(entry.getPlace(), entry.getRequestedStartsAt(), entry.getRequestedEndsAt());
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void expireOffers() {
        var due = waitlist.dueEntries(clock.instant(), PageRequest.of(0, WaitlistQueue.EXPIRY_BATCH_SIZE));
        Map<UUID, List<UUID>> byPlace = new LinkedHashMap<>();
        for (var entry : due) {
            byPlace.computeIfAbsent(entry.getPlaceId(), ignored -> new ArrayList<>()).add(entry.getEntryId());
        }
        byPlace.forEach(queue::expireEntries);
    }

    private void ensureOpen(WaitlistEntry entry) {
        if (entry.isDue(clock.instant())) {
            boolean released = entry.getStatus() == WaitlistStatus.OFFERED;
            entry.changeStatus(WaitlistStatus.EXPIRED);
            if (released) {
                queue.fillReleasedWindow(entry.getPlace(), entry.getRequestedStartsAt(), entry.getRequestedEndsAt());
            }
        }
        if (entry.getStatus() == WaitlistStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.GONE, "The waitlist entry has expired");
        }
    }

    private WaitlistEntry lockedEntry(UUID customerId, UUID entryId) {
        WaitlistEntry entry = waitlist.findById(entryId)
            .orElseThrow(() -> notFound("Waitlist entry not found"));
        if (!entry.getCustomer().getId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This waitlist entry belongs to another customer");
        }
        places.findLockedById(entry.getPlace().getId()).orElseThrow(() -> notFound("Place not found"));
        entityManager.refresh(entry);
        return entry;
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
