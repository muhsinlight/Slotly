package com.slotly.api.module.booking.repository;

import com.slotly.api.module.booking.entity.Appointment;
import com.slotly.api.module.booking.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.*;
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    @EntityGraph(attributePaths = {"customer", "place", "offering"})
    @Query("""
        select a from Appointment a
        where :term = '' or locate(:term, lower(translate(a.place.name, 'Iİ', 'ıi'))) > 0
            or locate(:term, lower(translate(a.offering.name, 'Iİ', 'ıi'))) > 0
            or locate(:term, lower(translate(a.customer.username, 'Iİ', 'ıi'))) > 0
        order by a.startsAt desc, a.id desc
        """)
    Page<Appointment> adminPage(String term, Pageable page);

    long countByStatusAndStartsAtGreaterThanEqual(AppointmentStatus status, Instant now);
    List<Appointment> findByPlaceIdAndStatusAndEndsAtGreaterThan(UUID placeId, AppointmentStatus status, Instant now);

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

    @EntityGraph(attributePaths = {"customer", "place", "offering"})
    List<Appointment> findByCustomerIdOrderByStartsAtDesc(UUID customerId);

    @EntityGraph(attributePaths = {"customer", "place", "offering"})
    List<Appointment> findByPlaceIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
        UUID placeId,
        Instant from,
        Instant to
    );
}
