package com.slotly.api.module.booking.service.impl;

import com.slotly.api.module.booking.service.BookingService;
import com.slotly.api.module.place.service.BusinessCalendarService;
import com.slotly.api.module.waitlist.service.impl.WaitlistOfferEvent;

import com.slotly.api.module.booking.dto.request.AppointmentRequest;
import com.slotly.api.module.place.dto.request.BusinessHourRequest;
import com.slotly.api.module.place.dto.request.OfferingRequest;
import com.slotly.api.module.place.dto.request.PlaceRequest;
import com.slotly.api.module.waitlist.dto.request.WaitlistRequest;
import com.slotly.api.module.booking.dto.response.AppointmentResponse;
import com.slotly.api.module.place.dto.response.BusinessHourResponse;
import com.slotly.api.module.place.dto.response.OfferingResponse;
import com.slotly.api.module.place.dto.response.PlaceResponse;
import com.slotly.api.module.waitlist.dto.response.WaitlistResponse;
import com.slotly.api.module.booking.entity.Appointment;
import com.slotly.api.module.booking.entity.AppointmentStatus;
import com.slotly.api.module.place.entity.BusinessHour;
import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.OfferingKind;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.waitlist.entity.WaitlistEntry;
import com.slotly.api.module.waitlist.entity.WaitlistStatus;
import com.slotly.api.module.booking.repository.AppointmentRepository;
import com.slotly.api.module.place.repository.BusinessHourRepository;
import com.slotly.api.module.place.repository.OfferingRepository;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.user.repository.UserRepository;
import com.slotly.api.module.waitlist.repository.WaitlistRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.TreeMap;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class BookingServiceImpl implements com.slotly.api.module.booking.service.BookingService {
    @PersistenceContext
    private EntityManager entityManager;
    private static final Duration WAITLIST_OFFER_LIFETIME = Duration.ofMinutes(30);
    private static final Duration MAX_AVAILABILITY_RANGE = Duration.ofDays(31);

    private final PlaceRepository places;
    private final OfferingRepository offerings;
    private final BusinessHourRepository hours;
    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final WaitlistRepository waitlist;
    private final ZoneId zone;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final BusinessCalendarService calendar;

    public BookingServiceImpl(
        PlaceRepository places,
        OfferingRepository offerings,
        BusinessHourRepository hours,
        AppointmentRepository appointments,
        UserRepository users,
        WaitlistRepository waitlist,
        Clock clock,
        ApplicationEventPublisher events,
        BusinessCalendarService calendar,
        @Value("${slotly.business.time-zone:Europe/Istanbul}") String zone
    ) {
        this.places = places;
        this.offerings = offerings;
        this.hours = hours;
        this.appointments = appointments;
        this.users = users;
        this.waitlist = waitlist;
        this.clock = clock;
        this.events = events;
        this.calendar = calendar;
        this.zone = ZoneId.of(zone);
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> publicPlaces() {
        return places.findByActiveTrue().stream().map(PlaceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PlaceResponse publicPlace(UUID id) {
        Place place = requirePlace(id);
        if (!place.isActive()) {
            throw notFound("Place not found");
        }
        return PlaceResponse.from(place);
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> myPlaces(UUID ownerId) {
        return places.findByOwnerId(ownerId).stream().map(PlaceResponse::from).toList();
    }

    @Transactional
    public PlaceResponse createPlace(UUID ownerId, PlaceRequest request) {
        User owner = users.findById(ownerId).orElseThrow(() -> notFound("Owner not found"));
        Place place = new Place(
            owner,
            request.name(),
            request.description(),
            request.address(),
            request.capacity()
        );
        return PlaceResponse.from(places.save(place));
    }

    @Transactional
    public PlaceResponse updatePlace(UUID ownerId, UUID placeId, PlaceRequest request) {
        Place place = places.findLockedById(placeId).orElseThrow(() -> notFound("Place not found"));
        requireOwnedPlace(ownerId, placeId);
        if (request.capacity() < place.getCapacity()) {
            validateCapacityReduction(placeId, request.capacity());
        }
        place.update(
            request.name(),
            request.description(),
            request.address(),
            request.capacity(),
            request.active()
        );
        return PlaceResponse.from(place);
    }

    private void validateCapacityReduction(UUID placeId, int capacity) {
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

    @Transactional
    public void deletePlace(UUID ownerId, UUID placeId) {
        Place place = requireOwnedPlace(ownerId, placeId);
        place.update(place.getName(), place.getDescription(), place.getAddress(), place.getCapacity(), false);
    }

    @Transactional(readOnly = true)
    public List<OfferingResponse> publicOfferings(UUID placeId) {
        if (!requirePlace(placeId).isActive()) {
            throw notFound("Place not found");
        }
        return offerings.findByPlaceIdAndActiveTrue(placeId).stream().map(OfferingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OfferingResponse> ownerOfferings(UUID ownerId, UUID placeId) {
        requireOwnedPlace(ownerId, placeId);
        return offerings.findByPlaceIdOrderByNameAsc(placeId)
            .stream()
            .map(OfferingResponse::from)
            .toList();
    }

    @Transactional
    public OfferingResponse createOffering(UUID ownerId, UUID placeId, OfferingRequest request) {
        Place place = requireOwnedPlace(ownerId, placeId);
        validateOffering(request);
        Offering offering = new Offering(
            place,
            request.name(),
            request.description(),
            request.price(),
            request.durationMinutes(),
            request.kind()
        );
        return OfferingResponse.from(offerings.save(offering));
    }

    @Transactional
    public OfferingResponse updateOffering(
        UUID ownerId,
        UUID placeId,
        UUID offeringId,
        OfferingRequest request
    ) {
        Place place = requireOwnedPlace(ownerId, placeId);
        Offering offering = requireOffering(offeringId);
        ensureOfferingBelongsToPlace(offering, place);
        validateOffering(request);
        offering.update(
            request.name(),
            request.description(),
            request.price(),
            request.durationMinutes(),
            request.kind(),
            request.active()
        );
        return OfferingResponse.from(offering);
    }

    @Transactional
    public void deleteOffering(UUID ownerId, UUID placeId, UUID offeringId) {
        Place place = requireOwnedPlace(ownerId, placeId);
        Offering offering = requireOffering(offeringId);
        ensureOfferingBelongsToPlace(offering, place);
        offering.update(
            offering.getName(),
            offering.getDescription(),
            offering.getPrice(),
            offering.getDurationMinutes(),
            offering.getKind(),
            false
        );
    }

    @Transactional(readOnly = true)
    public List<BusinessHourResponse> getHours(UUID placeId) {
        requirePlace(placeId);
        return hours.findByPlaceIdOrderByDayOfWeekAscOpensAtAsc(placeId)
            .stream()
            .map(BusinessHourResponse::from)
            .toList();
    }

    @Transactional
    public BusinessHourResponse addHour(UUID ownerId, UUID placeId, BusinessHourRequest request) {
        Place place = places.findLockedById(placeId).orElseThrow(() -> notFound("Place not found"));
        requireOwnedPlace(ownerId, placeId);
        if (!request.opensAt().isBefore(request.closesAt())) {
            throw bad("Closing time must be after opening time");
        }
        boolean overlaps = hours.findByPlaceIdOrderByDayOfWeekAscOpensAtAsc(placeId)
            .stream()
            .anyMatch(existing -> existing.getDayOfWeek() == request.dayOfWeek()
                && existing.getOpensAt().isBefore(request.closesAt())
                && existing.getClosesAt().isAfter(request.opensAt()));
        if (overlaps) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Business hour overlaps an existing interval");
        }
        return BusinessHourResponse.from(
            hours.save(new BusinessHour(place, request.dayOfWeek(), request.opensAt(), request.closesAt()))
        );
    }

    @Transactional
    public void deleteHour(UUID ownerId, UUID placeId, UUID hourId) {
        calendar.deleteHour(ownerId, placeId, hourId);
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
                    if (!slot.isBefore(Instant.now(clock)) && !end.isAfter(to) && hasCapacity(place, slot, end)) {
                        slots.add(slot);
                    }
                    slot = slot.plusSeconds(slotStepSeconds);
                }
            }
        }
        return slots;
    }

    @Transactional
    public AppointmentResponse createAppointment(UUID customerId, AppointmentRequest request) {
        Place place = places.findLockedById(request.placeId()).orElseThrow(() -> notFound("Place not found"));
        expirePlaceOffers(place);
        Offering offering = requireOffering(request.offeringId());
        ensureBookableOffering(place, offering);

        Instant startsAt = request.startsAt();
        Instant endsAt = startsAt.plusSeconds(offering.getDurationMinutes() * 60L);
        if (!startsAt.isAfter(Instant.now(clock))) {
            throw bad("Appointment must start in the future");
        }
        if (!isValidServiceSlot(place.getId(), offering, startsAt, endsAt)) {
            throw bad("Appointment is outside business hours");
        }
        if (appointments.existsByCustomerIdAndOfferingIdAndStartsAtAndStatus(
            customerId, offering.getId(), startsAt, AppointmentStatus.CONFIRMED
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have this appointment");
        }
        if (!hasCapacity(place, startsAt, endsAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Place has no capacity for this time");
        }

        User customer = users.findById(customerId).orElseThrow(() -> notFound("Customer not found"));
        Appointment appointment = appointments.save(new Appointment(customer, place, offering, startsAt, endsAt));
        return AppointmentResponse.from(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> myAppointments(UUID customerId) {
        return appointments.findByCustomerIdOrderByStartsAtDesc(customerId)
            .stream()
            .map(AppointmentResponse::from)
            .toList();
    }

    @Transactional
    public void cancel(UUID callerId, UUID appointmentId) {
        Appointment appointment = appointments.findById(appointmentId)
            .orElseThrow(() -> notFound("Appointment not found"));
        Place place = places.findLockedById(appointment.getPlace().getId())
            .orElseThrow(() -> notFound("Place not found"));
        appointment = appointments.findById(appointmentId)
            .orElseThrow(() -> notFound("Appointment not found"));
        entityManager.refresh(appointment);

        boolean isCustomer = appointment.getCustomer().getId().equals(callerId);
        boolean isOwner = place.getOwner().getId().equals(callerId);
        if (!isCustomer && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot cancel this appointment");
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appointment is already cancelled");
        }

        appointment.cancel();
        offerNext(place, appointment.getOffering(), appointment.getStartsAt());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> ownerAppointments(UUID ownerId, UUID placeId, Instant from, Instant to) {
        requireOwnedPlace(ownerId, placeId);
        if (!from.isBefore(to)) {
            throw bad("Invalid time range");
        }
        return appointments.findByPlaceIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                placeId,
                from,
                to
            )
            .stream()
            .map(AppointmentResponse::from)
            .toList();
    }

    @Transactional
    public WaitlistResponse joinWaitlist(UUID customerId, WaitlistRequest request) {
        Place place = places.findLockedById(request.placeId()).orElseThrow(() -> notFound("Place not found"));
        expirePlaceOffers(place);
        Offering offering = requireOffering(request.offeringId());
        ensureBookableOffering(place, offering);

        Instant startsAt = request.startsAt();
        Instant endsAt = startsAt.plusSeconds(offering.getDurationMinutes() * 60L);
        if (!startsAt.isAfter(Instant.now(clock))) {
            throw bad("Requested time must be in the future");
        }
        if (!isValidServiceSlot(place.getId(), offering, startsAt, endsAt)) {
            throw bad("Requested time is outside business hours");
        }
        if (hasCapacity(place, startsAt, endsAt)) {
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
        if (!hasCapacity(place, entry.getRequestedStartsAt(), entry.getRequestedEndsAt(), entry.getId())) {
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

    private void offerNext(Place place, Offering offering, Instant startsAt) {
        if (!startsAt.isAfter(Instant.now(clock))) {
            return;
        }
        Instant endsAt = startsAt.plusSeconds(offering.getDurationMinutes() * 60L);
        if (!hasCapacity(place, startsAt, endsAt)) {
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

    private void ensureBookableOffering(Place place, Offering offering) {
        if (!place.isActive() || !offering.isActive() || offering.getKind() != OfferingKind.SERVICE
            || offering.getDurationMinutes() == null || !offering.getPlace().getId().equals(place.getId())) {
            throw bad("Offering is not bookable at this place");
        }
    }

    private boolean hasCapacity(Place place, Instant startsAt, Instant endsAt) {
        return hasCapacity(place, startsAt, endsAt, null);
    }

    private void expirePlaceOffers(Place place) {
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

    private boolean hasCapacity(Place place, Instant startsAt, Instant endsAt, UUID acceptedOfferId) {
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

    private boolean isValidServiceSlot(UUID placeId, Offering offering, Instant startsAt, Instant endsAt) {
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

    private void validateOffering(OfferingRequest request) {
        if (request.kind() == OfferingKind.SERVICE
            && (request.durationMinutes() == null || request.durationMinutes() < 1)) {
            throw bad("Services require a positive duration");
        }
        if (request.kind() == OfferingKind.PRODUCT && request.durationMinutes() != null) {
            throw bad("Products cannot have an appointment duration");
        }
    }

    private void ensureOfferingBelongsToPlace(Offering offering, Place place) {
        if (!offering.getPlace().getId().equals(place.getId())) {
            throw notFound("Offering not found");
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

    private Place requirePlace(UUID id) {
        return places.findById(id).orElseThrow(() -> notFound("Place not found"));
    }

    private Place requireOwnedPlace(UUID ownerId, UUID placeId) {
        Place place = requirePlace(placeId);
        if (!place.getOwner().getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Place belongs to another owner");
        }
        return place;
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
