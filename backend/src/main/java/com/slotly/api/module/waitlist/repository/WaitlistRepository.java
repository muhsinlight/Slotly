package com.slotly.api.module.waitlist.repository;

import com.slotly.api.module.waitlist.entity.WaitlistEntry;
import com.slotly.api.module.waitlist.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {
    String WAITING_CANDIDATES = """
        select e from WaitlistEntry e
        where e.place.id = :placeId and e.status = WAITING and e.requestedStartsAt > :now
        and not exists (select a.id from Appointment a
            where a.customer = e.customer and a.offering = e.offering
            and a.startsAt = e.requestedStartsAt and a.status = CONFIRMED)
        """;

    @EntityGraph(attributePaths = {"customer", "place", "offering"})
    @Query("""
        select e from WaitlistEntry e
        where :term = '' or locate(:term, lower(translate(e.place.name, 'Iİ', 'ıi'))) > 0
            or locate(:term, lower(translate(e.offering.name, 'Iİ', 'ıi'))) > 0
            or locate(:term, lower(translate(e.customer.username, 'Iİ', 'ıi'))) > 0
        order by e.createdAt desc, e.id desc
        """)
    Page<WaitlistEntry> adminPage(String term, Pageable page);

    long countByStatusAndRequestedStartsAtGreaterThan(WaitlistStatus status, Instant now);

    long countByStatusAndOfferExpiresAtGreaterThan(WaitlistStatus status, Instant now);

    List<WaitlistEntry> findByPlaceIdAndStatusAndOfferExpiresAtGreaterThan(UUID placeId, WaitlistStatus status, Instant now);

    @EntityGraph(attributePaths = {"place", "offering"})
    List<WaitlistEntry> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @EntityGraph(attributePaths = "offering")
    @Query(WAITING_CANDIDATES + " order by e.createdAt, e.id")
    List<WaitlistEntry> waitingCandidates(UUID placeId, Instant now);

    @EntityGraph(attributePaths = "offering")
    @Query(WAITING_CANDIDATES + """
        and e.requestedStartsAt < :endsAt and e.requestedEndsAt > :startsAt
        order by e.createdAt, e.id
        """)
    List<WaitlistEntry> waitingInWindow(UUID placeId, Instant now, Instant startsAt, Instant endsAt);

    boolean existsByCustomerIdAndPlaceIdAndOfferingIdAndRequestedStartsAtAndStatusIn(
        UUID customerId,
        UUID placeId,
        UUID offeringId,
        Instant startsAt,
        Collection<WaitlistStatus> statuses
    );

    interface DueEntry {
        UUID getPlaceId();
        UUID getEntryId();
    }

    @Query("""
        select e.place.id as placeId, e.id as entryId from WaitlistEntry e
        where (e.status = OFFERED and e.offerExpiresAt <= :now)
            or (e.status = WAITING and e.requestedStartsAt <= :now)
        order by e.requestedStartsAt, e.createdAt, e.id
        """)
    List<DueEntry> dueEntries(Instant now, Pageable limit);

    @Query("""
        select e from WaitlistEntry e where e.place.id = :placeId
            and e.status = OFFERED and e.offerExpiresAt <= :now
            and e.requestedStartsAt < :endsAt and e.requestedEndsAt > :startsAt
        order by e.offerExpiresAt, e.id
        """)
    List<WaitlistEntry> expiredOffersInWindow(UUID placeId, Instant now, Instant startsAt, Instant endsAt, Pageable limit);

    List<WaitlistEntry> findByPlaceIdAndStatusAndOfferExpiresAtGreaterThanAndRequestedStartsAtLessThanAndRequestedEndsAtGreaterThan(
        UUID placeId,
        WaitlistStatus status,
        Instant now,
        Instant end,
        Instant start
    );
}
