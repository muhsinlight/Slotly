package com.slotly.api.module.booking.entity;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments", indexes = @Index(columnList = "place_id,starts_at,ends_at"))
public class Appointment {
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

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    protected Appointment() {
    }

    public Appointment(User customer, Place place, Offering offering, Instant startsAt, Instant endsAt) {
        this.customer = customer;
        this.place = place;
        this.offering = offering;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
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

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void recordOutcome(AppointmentStatus outcome, Instant now) {
        if (outcome != AppointmentStatus.COMPLETED && outcome != AppointmentStatus.NO_SHOW) {
            throw new IllegalArgumentException("Outcome must be COMPLETED or NO_SHOW");
        }
        if (now.isBefore(endsAt)) {
            throw new IllegalStateException("Appointment has not ended yet");
        }
        if (status == outcome) {
            return;
        }
        if (status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Appointment outcome cannot be changed");
        }
        status = outcome;
    }

    public void cancel() {
        if (status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed appointments can be cancelled");
        }
        status = AppointmentStatus.CANCELLED;
    }
}
