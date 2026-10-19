package com.dev.seatbooking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HoldRequest(
        @NotEmpty(message = "seatIds must not be empty")
        @Size(max = 6, message = "cannot hold more than 6 seats")
        List<@NotNull(message = "seat id cannot be null") Long> seatIds
) {}
