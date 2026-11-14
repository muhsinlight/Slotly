package com.slotly.api.module.place.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlaceRequest(
    @NotBlank
    @Size(max = 120)
    String name,
    @Size(max = 1000)
    String description,
    @NotBlank
    @Size(max = 300)
    String address,
    @Min(1)
    @Max(10000)
    int capacity,
    @NotNull Boolean active
) {
}
