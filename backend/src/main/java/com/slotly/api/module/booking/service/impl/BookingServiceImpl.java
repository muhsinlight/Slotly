package com.slotly.api.module.booking.service.impl;

import com.slotly.api.module.booking.dto.request.AppointmentRequest;
import com.slotly.api.module.booking.dto.response.AppointmentResponse;
import com.slotly.api.module.booking.entity.Appointment;
import com.slotly.api.module.booking.entity.AppointmentStatus;
import com.slotly.api.module.booking.repository.AppointmentRepository;
import com.slotly.api.module.booking.service.AvailabilityService;
import com.slotly.api.module.booking.service.BookingCapacityService;
import com.slotly.api.module.booking.service.BookingService;
import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.place.repository.OfferingRepository;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.repository.UserRepository;
import com.slotly.api.module.waitlist.service.WaitlistService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BookingServiceImpl implements BookingService {
    @PersistenceContext
    private EntityManager entityManager;

    private final PlaceRepository places;
    private final OfferingRepository offerings;
    private final AppointmentRepository appointments;
    private final UserRepository users;
    private final BookingCapacityService capacity;
    private final AvailabilityService availability;
    private final WaitlistService waitlist;
    private final Clock clock;

    public BookingServiceImpl(
        PlaceRepository places,
        OfferingRepository offerings,
        AppointmentRepository appointments,
        UserRepository users,
        BookingCapacityService capacity,
        AvailabilityService availability,
        WaitlistService waitlist,
        Clock clock
    ) {
        this.places = places;
        this.offerings = offerings;
        this.appointments = appointments;
        this.users = users;
        this.capacity = capacity;
        this.availability = availability;
        this.waitlist = waitlist;
        this.clock = clock;
    }

    @Transactional
    public AppointmentResponse createAppointment(UUID customerId, AppointmentRequest request) {
        Place place = places.findLockedById(request.placeId()).orElseThrow(() -> notFound("Place not found"));
        waitlist.expirePlaceOffers(place);
        Offering offering = requireOffering(request.offeringId());
        availability.ensureBookableOffering(place, offering);

        Instant startsAt = request.startsAt();
        Instant endsAt = startsAt.plusSeconds(offering.getDurationMinutes() * 60L);
        if (!startsAt.isAfter(Instant.now(clock))) {
            throw bad("Appointment must start in the future");
        }
        if (!availability.isValidServiceSlot(place.getId(), offering, startsAt, endsAt)) {
            throw bad("Appointment is outside business hours");
        }
        if (appointments.existsByCustomerIdAndOfferingIdAndStartsAtAndStatus(
            customerId, offering.getId(), startsAt, AppointmentStatus.CONFIRMED
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have this appointment");
        }
        if (!capacity.hasCapacity(place, startsAt, endsAt)) {
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
        waitlist.offerNext(place, appointment.getOffering(), appointment.getStartsAt());
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
