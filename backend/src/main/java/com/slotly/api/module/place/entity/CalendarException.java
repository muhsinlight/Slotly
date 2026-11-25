package com.slotly.api.module.place.entity;

import com.slotly.api.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "calendar_exceptions", uniqueConstraints = @UniqueConstraint(columnNames = {"place_id", "date"}))
public class CalendarException extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private boolean closed;

    private LocalTime opensAt;
    private LocalTime closesAt;

    protected CalendarException() {
    }

    public CalendarException(Place place, LocalDate date) {
        this.place = place;
        this.date = date;
    }

    public void update(boolean closed, LocalTime opensAt, LocalTime closesAt) {
        this.closed = closed;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public boolean isClosed() {
        return closed;
    }

    public LocalTime getOpensAt() {
        return opensAt;
    }

    public LocalTime getClosesAt() {
        return closesAt;
    }
}
