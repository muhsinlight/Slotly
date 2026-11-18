package com.slotly.api.module.place.entity;

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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "offerings")
public class Offering {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OfferingKind kind;

    @Column(nullable = false)
    private boolean active = true;

    protected Offering() {
    }

    public Offering(
        Place place,
        String name,
        String description,
        BigDecimal price,
        Integer durationMinutes,
        OfferingKind kind
    ) {
        this(place, name, description, price, durationMinutes, kind, true);
    }

    public Offering(
        Place place,
        String name,
        String description,
        BigDecimal price,
        Integer durationMinutes,
        OfferingKind kind,
        boolean active
    ) {
        this.place = place;
        update(name, description, price, durationMinutes, kind, active);
    }

    public UUID getId() {
        return id;
    }

    public Place getPlace() {
        return place;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public OfferingKind getKind() {
        return kind;
    }

    public boolean isActive() {
        return active;
    }

    public void update(
        String name,
        String description,
        BigDecimal price,
        Integer durationMinutes,
        OfferingKind kind,
        boolean active
    ) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.durationMinutes = durationMinutes;
        this.kind = kind;
        this.active = active;
    }
}
