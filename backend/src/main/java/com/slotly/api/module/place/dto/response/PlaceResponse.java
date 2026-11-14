package com.slotly.api.module.place.dto.response;

import com.slotly.api.module.place.entity.Place;

import java.util.UUID;

public record PlaceResponse(
    UUID id,
    UUID ownerId,
    String name,
    String description,
    String address,
    int capacity,
    boolean active
) {
    public static PlaceResponse from(Place place) {
        return new PlaceResponse(
            place.getId(),
            place.getOwner().getId(),
            place.getName(),
            place.getDescription(),
            place.getAddress(),
            place.getCapacity(),
            place.isActive()
        );
    }
}
