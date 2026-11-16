package com.slotly.api.module.waitlist.service.impl;

import com.slotly.api.module.booking.dto.response.AppointmentResponse;
import com.slotly.api.module.booking.entity.Appointment;
import com.slotly.api.module.booking.repository.AppointmentRepository;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WaitlistServiceImpl implements WaitlistService {
    @PersistenceContext
    private EntityManager entityManager;

    private static final Duration WAITLIST_OFFER_LIFETIME = Duration.ofMinutes(30);
    private final PlaceRepository places;
    private final OfferingRepository offerings;
    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final WaitlistRepository waitlist;
    private final BookingCapacityService capacity;
    private final AvailabilityService availability;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public WaitlistServiceImpl(
        PlaceRepository places,
        OfferingRepository offerings,
        AppointmentRepository appointments,
        UserRepository users,
        WaitlistRepository waitlist,
        BookingCapacityService capacity,
        AvailabilityService availability,
        Clock clock,
        ApplicationEventPublisher events
    ) {
        this.places = places;
        this.offerings = offerings;
        this.appointments = appointments;
        this.users = users;
        this.waitlist = waitlist;
        this.capacity = capacity;
        this.availability = availability;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public WaitlistResponse joinWaitlist(UUID customerId, WaitlistRequest request) {
        Place place = places.findLockedById(request.placeId()).orElseThrow(() -> notFound("Place not found"));
        expirePlaceOffers(place);
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

    @Transactional
    public List<WaitlistResponse> myWaitlist(UUID customerId) {
        expireOffers();
        return waitlist.findByCustomerIdOrderByCreatedAtDesc(customerId)
            .stream()
            .map(WaitlistResponse::from)
            .toList();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AppointmentResponse acceptWaitlistOffer(UUID customerId, UUID entryId) {
        WaitlistEntry entry = requireWaitlistEntry(customerId, entryId);
        Place place = places.findLockedById(entry.getPlace().getId()).orElseThrow(() -> notFound("Place not found"));
        entry = requireWaitlistEntry(customerId, entryId);
        entityManager.refresh(entry);
        if (entry.getStatus() != WaitlistStatus.OFFERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no active offer for this entry");
        }
        if (!entry.getOfferExpiresAt().isAfter(Instant.now(clock))) {
            entry.changeStatus(WaitlistStatus.EXPIRED);
            offerNext(place, entry.getOffering(), entry.getRequestedStartsAt());
            throw new ResponseStatusException(HttpStatus.GONE, "The waitlist offer has expired");
        }
        if (!entry.getRequestedStartsAt().isAfter(Instant.now(clock))) {
            entry.changeStatus(WaitlistStatus.EXPIRED);
            throw new ResponseStatusException(HttpStatus.GONE, "The requested time has passed");
        }
        if (!capacity.hasCapacity(place, entry.getRequestedStartsAt(), entry.getRequestedEndsAt(), entry.getId())) {
            entry.changeStatus(WaitlistStatus.EXPIRED);
            offerNext(place, entry.getOffering(), entry.getRequestedStartsAt());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The requested time is no longer available");
        }

        entry.changeStatus(WaitlistStatus.ACCEPTED);
        Appointment appointment = appointments.save(new Appointment(
            entry.getCustomer(),
            place,
            entry.getOffering(),
            entry.getRequestedStartsAt(),
            entry.getRequestedEndsAt()
        ));
        offerNext(place, entry.getOffering(), entry.getRequestedStartsAt());
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public WaitlistResponse declineWaitlistOffer(UUID customerId, UUID entryId) {
        WaitlistEntry entry = requireWaitlistEntry(customerId, entryId);
        Place place = places.findLockedById(entry.getPlace().getId()).orElseThrow(() -> notFound("Place not found"));
        entry = requireWaitlistEntry(customerId, entryId);
        entityManager.refresh(entry);
        if (entry.getStatus() != WaitlistStatus.OFFERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is no active offer for this entry");
        }
        entry.changeStatus(WaitlistStatus.DECLINED);
        offerNext(place, entry.getOffering(), entry.getRequestedStartsAt());
        return WaitlistResponse.from(entry);
    }

    @Transactional
    public void leaveWaitlist(UUID customerId, UUID entryId) {
        WaitlistEntry entry = requireWaitlistEntry(customerId, entryId);
        Place place = places.findLockedById(entry.getPlace().getId()).orElseThrow(() -> notFound("Place not found"));
        entry = requireWaitlistEntry(customerId, entryId);
        entityManager.refresh(entry);
        boolean hadOffer = entry.getStatus() == WaitlistStatus.OFFERED;
        if (entry.getStatus() != WaitlistStatus.WAITING && !hadOffer) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This waitlist entry is already closed");
        }
        entry.changeStatus(WaitlistStatus.LEFT);
        if (hadOffer) {
            offerNext(place, entry.getOffering(), entry.getRequestedStartsAt());
        }
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void expireOffers() {
        Instant now = Instant.now(clock);
        List<WaitlistEntry> expiredOffers = waitlist.findByStatusAndOfferExpiresAtLessThanEqual(
            WaitlistStatus.OFFERED,
            now
        );
        for (WaitlistEntry candidate : expiredOffers) {
            Place place = places.findLockedById(candidate.getPlace().getId()).orElse(null);
            if (place == null) {
                continue;
            }
            WaitlistEntry entry = waitlist.findById(candidate.getId()).orElse(null);
            if (entry != null) {
                entityManager.refresh(entry);
            }
            if (entry == null || entry.getStatus() != WaitlistStatus.OFFERED
                || entry.getOfferExpiresAt().isAfter(now)) {
                continue;
            }
            entry.changeStatus(WaitlistStatus.EXPIRED);
            offerNext(place, entry.getOffering(), entry.getRequestedStartsAt());
        }
    }

    public void offerNext(Place place, Offering offering, Instant startsAt) {
        if (!startsAt.isAfter(Instant.now(clock))) {
            return;
        }
        Instant endsAt = startsAt.plusSeconds(offering.getDurationMinutes() * 60L);
        if (!capacity.hasCapacity(place, startsAt, endsAt)) {
            return;
        }
        List<WaitlistEntry> activeOffers = waitlist
            .findByPlaceIdAndOfferingIdAndRequestedStartsAtAndStatusOrderByCreatedAtAsc(
                place.getId(), offering.getId(), startsAt, WaitlistStatus.OFFERED
            );
        if (!activeOffers.isEmpty()) {
            return;
        }
        List<WaitlistEntry> waitingEntries = waitlist
            .findByPlaceIdAndOfferingIdAndRequestedStartsAtAndStatusOrderByCreatedAtAsc(
                place.getId(), offering.getId(), startsAt, WaitlistStatus.WAITING
            );
        if (waitingEntries.isEmpty()) {
            return;
        }
        Instant now = Instant.now(clock);
        WaitlistEntry nextCustomer = waitingEntries.getFirst();
        Instant expiresAt = now.plus(WAITLIST_OFFER_LIFETIME);
        nextCustomer.offer(now, expiresAt);
        events.publishEvent(new WaitlistOfferEvent(nextCustomer.getCustomer().getId(), nextCustomer.getId(), expiresAt));
    }

    public void expirePlaceOffers(Place place) {
        List<WaitlistEntry> expired = waitlist.findByPlaceIdAndStatusAndOfferExpiresAtLessThanEqual(
            place.getId(), WaitlistStatus.OFFERED, Instant.now(clock)
        );
        for (WaitlistEntry entry : expired) {
            entityManager.refresh(entry);
            if (entry.getStatus() == WaitlistStatus.OFFERED) {
                entry.changeStatus(WaitlistStatus.EXPIRED);
                offerNext(place, entry.getOffering(), entry.getRequestedStartsAt());
            }
        }
    }

    private WaitlistEntry requireWaitlistEntry(UUID customerId, UUID entryId) {
        WaitlistEntry entry = waitlist.findById(entryId)
            .orElseThrow(() -> notFound("Waitlist entry not found"));
        if (!entry.getCustomer().getId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This waitlist entry belongs to another customer");
        }
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
