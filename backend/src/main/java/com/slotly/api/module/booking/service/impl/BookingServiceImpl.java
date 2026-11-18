package com.slotly.api.module.booking.service.impl;

import com.slotly.api.common.aop.OwnedPlace;
import com.slotly.api.module.booking.dto.request.AppointmentRequest;
import com.slotly.api.module.booking.dto.response.AppointmentResponse;
import com.slotly.api.module.booking.entity.Appointment;
import com.slotly.api.module.booking.entity.AppointmentStatus;
import com.slotly.api.module.booking.repository.AppointmentRepository;
import com.slotly.api.module.booking.service.AppointmentReservation;
import com.slotly.api.module.booking.service.BookingService;
import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.place.repository.OfferingRepository;
import com.slotly.api.module.place.repository.PlaceRepository;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.repository.UserRepository;
import com.slotly.api.module.waitlist.service.WaitlistQueue;
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
    private final AppointmentReservation reservations;
    private final WaitlistQueue queue;
    private final Clock clock;

    public BookingServiceImpl(
        PlaceRepository places,
        OfferingRepository offerings,
        AppointmentRepository appointments,
        UserRepository users,
        AppointmentReservation reservations,
        WaitlistQueue queue,
        Clock clock
    ) {
        this.places = places;
        this.offerings = offerings;
        this.appointments = appointments;
        this.users = users;
        this.reservations = reservations;
        this.queue = queue;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AppointmentResponse createAppointment(UUID customerId, AppointmentRequest request) {
        Place place = places.findLockedById(request.placeId()).orElseThrow(() -> notFound("Place not found"));
        Offering offering = requireOffering(request.offeringId());
        User customer = users.findById(customerId).orElseThrow(() -> notFound("Customer not found"));
        Appointment appointment = reservations.reserve(customer, place, offering, request.startsAt(), null);
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
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed appointments can be cancelled");
        }

        appointment.cancel();
        queue.fillReleasedWindow(place, appointment.getStartsAt(), appointment.getEndsAt());
    }

    @Transactional(readOnly = true)
    @OwnedPlace
    public List<AppointmentResponse> ownerAppointments(UUID ownerId, UUID placeId, Instant from, Instant to) {

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
    public AppointmentResponse recordOutcome(UUID ownerId, UUID appointmentId, AppointmentStatus outcome) {
        Appointment appointment = appointments.findById(appointmentId)
            .orElseThrow(() -> notFound("Appointment not found"));
        Place place = places.findLockedById(appointment.getPlace().getId())
            .orElseThrow(() -> notFound("Place not found"));
        if (!place.getOwner().getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Place belongs to another owner");
        }
        entityManager.refresh(appointment);
        try {
            appointment.recordOutcome(outcome, clock.instant());
        } catch (IllegalArgumentException exception) {
            throw bad(exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        return AppointmentResponse.from(appointment);
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
