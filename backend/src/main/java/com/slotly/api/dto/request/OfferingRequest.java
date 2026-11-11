package com.slotly.api.dto.request;

import com.slotly.api.entity.OfferingKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OfferingRequest(
    @NotBlank
    @Size(max = 120)
    String name,
    @Size(max = 1000)
    String description,
    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    BigDecimal price,
    @Min(1)
    @Max(1440)
    Integer durationMinutes,
    @NotNull OfferingKind kind,
    @NotNull Boolean active
) {
}
