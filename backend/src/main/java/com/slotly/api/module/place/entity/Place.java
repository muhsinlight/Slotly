package com.slotly.api.module.place.entity;

import com.slotly.api.common.persistence.AuditableEntity;
import com.slotly.api.module.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "places")
public class Place extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean active = true;

    protected Place() {
    }

    public Place(
        User owner,
        String name,
        String description,
        String address,
        int capacity
    ) {
        this(owner, name, description, address, capacity, true);
    }

    public Place(
        User owner,
        String name,
        String description,
        String address,
        int capacity,
        boolean active
    ) {
        this.owner = owner;
        update(name, description, address, capacity, active);
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getAddress() {
        return address;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isActive() {
        return active;
    }

    public void update(
        String name,
        String description,
        String address,
        int capacity,
        boolean active
    ) {
        this.name = name;
        this.description = description;
        this.address = address;
        this.capacity = capacity;
        this.active = active;
    }
}
