package com.dev.seatbooking.dto;

import java.time.Instant;

public record HoldResponse(Long holdId, Instant expiresAt) {}
