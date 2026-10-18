package com.dev.seatbooking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BulkSeatsRequest(
        @NotNull @Min(1) @Max(26) Integer rows,
        @NotNull @Min(1) @Max(200) Integer columns,
        @NotNull @Min(0) @Max(100_000_000) Integer priceCents) {}

