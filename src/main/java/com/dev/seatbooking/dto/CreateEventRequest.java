package com.dev.seatbooking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateEventRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String venue,
        @NotNull @Future Instant startsAt) {}
