package com.slotly.api.repository;

import com.slotly.api.entity.WaitlistEntry;
import com.slotly.api.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {
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
}
