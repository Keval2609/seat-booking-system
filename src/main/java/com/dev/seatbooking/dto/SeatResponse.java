package com.dev.seatbooking.dto;

import com.dev.seatbooking.entity.SeatStatus;

public record SeatResponse(Long id, String label, int priceCents, SeatStatus status) {}
