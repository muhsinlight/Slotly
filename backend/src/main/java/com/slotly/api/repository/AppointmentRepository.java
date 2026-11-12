package com.slotly.api.repository;
import com.slotly.api.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    List<Appointment> findByPlaceIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
        UUID placeId,
        AppointmentStatus status,
        Instant end,
        Instant start
    );

    boolean existsByCustomerIdAndOfferingIdAndStartsAtAndStatus(
        UUID customerId,
        UUID offeringId,
        Instant startsAt,
        AppointmentStatus status
    );

    List<Appointment> findByCustomerIdOrderByStartsAtDesc(UUID customerId);

    List<Appointment> findByPlaceIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
        UUID placeId,
        Instant from,
        Instant to
    );
}
