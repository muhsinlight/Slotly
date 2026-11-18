package com.slotly.api.module.waitlist.entity;

import com.slotly.api.module.place.entity.Offering;
import com.slotly.api.module.place.entity.Place;
import com.slotly.api.module.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private Offering offering;

    @Column(name = "requested_starts_at", nullable = false)
    private Instant requestedStartsAt;

    @Column(name = "requested_ends_at", nullable = false)
    private Instant requestedEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private WaitlistStatus status = WaitlistStatus.WAITING;

    @Column(name = "offered_at")
    private Instant offeredAt;

    @Column(name = "offer_expires_at")
    private Instant offerExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected WaitlistEntry() {
    }

    public WaitlistEntry(User customer, Place place, Offering offering, Instant startsAt, Instant endsAt) {
        this.customer = customer;
        this.place = place;
        this.offering = offering;
        this.requestedStartsAt = startsAt;
        this.requestedEndsAt = endsAt;
    }

    public UUID getId() {
        return id;
    }

    public User getCustomer() {
        return customer;
    }

    public Place getPlace() {
        return place;
    }

    public Offering getOffering() {
        return offering;
    }

    public Instant getRequestedStartsAt() {
        return requestedStartsAt;
    }

    public Instant getRequestedEndsAt() {
        return requestedEndsAt;
    }

    public WaitlistStatus getStatus() {
        return status;
    }

    public Instant getOfferedAt() {
        return offeredAt;
    }

    public Instant getOfferExpiresAt() {
        return offerExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void offer(Instant offeredAt, Instant expiresAt) {
        this.status = WaitlistStatus.OFFERED;
        this.offeredAt = offeredAt;
        this.offerExpiresAt = expiresAt;
    }

    public boolean isDue(Instant now) {
        return (status == WaitlistStatus.WAITING && !requestedStartsAt.isAfter(now))
            || (status == WaitlistStatus.OFFERED && (!offerExpiresAt.isAfter(now) || !requestedStartsAt.isAfter(now)));
    }

    public void changeStatus(WaitlistStatus status) {
        this.status = status;
    }
}
