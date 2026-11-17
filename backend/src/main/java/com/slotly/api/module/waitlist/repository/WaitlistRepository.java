package com.slotly.api.module.waitlist.repository;

import com.slotly.api.module.waitlist.entity.WaitlistEntry;
import com.slotly.api.module.waitlist.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {
    @EntityGraph(attributePaths = {"customer", "place", "offering"})
    List<WaitlistEntry> findAllByOrderByCreatedAtDesc();
    List<WaitlistEntry> findByPlaceIdAndStatusAndOfferExpiresAtGreaterThan(UUID placeId, WaitlistStatus status, Instant now);

    List<WaitlistEntry> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<WaitlistEntry> findByPlaceIdAndOfferingIdAndRequestedStartsAtAndStatusOrderByCreatedAtAsc(
        UUID placeId,
        UUID offeringId,
        Instant startsAt,
        WaitlistStatus status
    );

    boolean existsByCustomerIdAndPlaceIdAndOfferingIdAndRequestedStartsAtAndStatusIn(
        UUID customerId,
        UUID placeId,
        UUID offeringId,
        Instant startsAt,
        Collection<WaitlistStatus> statuses
    );

    List<WaitlistEntry> findByStatusAndOfferExpiresAtLessThanEqual(WaitlistStatus status, Instant now);

    List<WaitlistEntry> findByPlaceIdAndStatusAndOfferExpiresAtLessThanEqual(
        UUID placeId,
        WaitlistStatus status,
        Instant now
    );

    List<WaitlistEntry> findByPlaceIdAndStatusAndOfferExpiresAtGreaterThanAndRequestedStartsAtLessThanAndRequestedEndsAtGreaterThan(
        UUID placeId,
        WaitlistStatus status,
        Instant now,
        Instant end,
        Instant start
    );
}
