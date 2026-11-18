package com.slotly.api.module.place.service;

import com.slotly.api.common.aop.OwnedPlace;
import com.slotly.api.module.booking.service.BookingCapacityService;
import com.slotly.api.module.place.dto.request.BusinessHourRequest;
import com.slotly.api.module.place.dto.request.OfferingRequest;
import com.slotly.api.module.place.dto.request.PlaceRequest;
import com.slotly.api.module.place.dto.response.BusinessHourResponse;
import com.slotly.api.module.place.dto.response.OfferingResponse;
import com.slotly.api.module.place.dto.response.PlaceResponse;
import com.slotly.api.module.place.entity.BusinessHour;
import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.OfferingKind;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.place.repository.BusinessHourRepository;
import com.slotly.api.module.place.repository.OfferingRepository;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.repository.UserRepository;
import com.slotly.api.module.waitlist.service.WaitlistQueue;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class PlaceServiceImpl implements PlaceService {
    private final PlaceRepository places;
    private final OfferingRepository offerings;
    private final BusinessHourRepository hours;
    private final UserRepository users;
    private final BusinessCalendarService calendar;
    private final BookingCapacityService capacity;
    private final WaitlistQueue waitlist;

    public PlaceServiceImpl(
        PlaceRepository places,
        OfferingRepository offerings,
        BusinessHourRepository hours,
        UserRepository users,
        BusinessCalendarService calendar,
        BookingCapacityService capacity,
        WaitlistQueue waitlist
    ) {
        this.places = places;
        this.offerings = offerings;
        this.hours = hours;
        this.users = users;
        this.calendar = calendar;
        this.capacity = capacity;
        this.waitlist = waitlist;
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> publicPlaces() {
        return places.findByActiveTrue().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PlaceResponse publicPlace(UUID id) {
        Place place = requirePlace(id);
        if (!place.isActive()) {
            throw notFound("Place not found");
        }
        return toResponse(place);
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> myPlaces(UUID ownerId) {
        return places.findByOwnerId(ownerId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PlaceResponse createPlace(UUID ownerId, PlaceRequest request) {
        User owner = users.findById(ownerId).orElseThrow(() -> notFound("Owner not found"));
        Place place = new Place(
            owner,
            request.name(),
            request.description(),
            request.address(),
            request.capacity(),
            request.active()
        );
        return toResponse(places.save(place));
    }

    @Transactional
    @OwnedPlace(lock = true)
    public PlaceResponse updatePlace(UUID ownerId, UUID placeId, PlaceRequest request) {
        Place place = requirePlace(placeId);

        int previousCapacity = place.getCapacity();
        if (request.capacity() < previousCapacity) {
            capacity.validateCapacityReduction(placeId, request.capacity());
        }
        place.update(
            request.name(),
            request.description(),
            request.address(),
            request.capacity(),
            request.active()
        );
        if (request.capacity() > previousCapacity && place.isActive()) {
            waitlist.fillAvailableCapacity(place);
        }
        return toResponse(place);
    }

    @Transactional
    @OwnedPlace(lock = true)
    public void deletePlace(UUID ownerId, UUID placeId) {
        Place place = requirePlace(placeId);
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
    @OwnedPlace
    public List<OfferingResponse> ownerOfferings(UUID ownerId, UUID placeId) {

        return offerings.findByPlaceIdOrderByNameAsc(placeId)
            .stream()
            .map(OfferingResponse::from)
            .toList();
    }

    @Transactional
    @OwnedPlace
    public OfferingResponse createOffering(UUID ownerId, UUID placeId, OfferingRequest request) {
        Place place = requirePlace(placeId);
        validateOffering(request);
        Offering offering = new Offering(
            place,
            request.name(),
            request.description(),
            request.price(),
            request.durationMinutes(),
            request.kind(),
            request.active()
        );
        return OfferingResponse.from(offerings.save(offering));
    }

    @Transactional
    @OwnedPlace(lock = true)
    public OfferingResponse updateOffering(
        UUID ownerId,
        UUID placeId,
        UUID offeringId,
        OfferingRequest request
    ) {
        Place place = requirePlace(placeId);
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
    @OwnedPlace(lock = true)
    public void deleteOffering(UUID ownerId, UUID placeId, UUID offeringId) {
        Place place = requirePlace(placeId);
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
    @OwnedPlace(lock = true)
    public BusinessHourResponse addHour(UUID ownerId, UUID placeId, BusinessHourRequest request) {
        Place place = requirePlace(placeId);

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

    private PlaceResponse toResponse(Place place) {
        return PlaceResponse.from(place, calendar.timeZone());
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
