package com.dev.seatbooking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record BulkSeatsRequest(
        @Min(1) @Max(26) int rows,
        @Min(1) @Max(200) int columns,
        @Min(0) int priceCents) {}
