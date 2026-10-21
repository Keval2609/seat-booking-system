package com.dev.seatbooking.error;

import java.util.List;

public class OptimisticConflictException extends RuntimeException {

    private final List<Long> seatIds;

    public OptimisticConflictException() {
        this((Long) null);
    }

    public OptimisticConflictException(Long seatId) {
        super("Seat version changed", null, false, false);
        this.seatIds = seatId != null ? List.of(seatId) : List.of();
    }

    public OptimisticConflictException(List<Long> seatIds) {
        super("Seat version changed", null, false, false);
        this.seatIds = seatIds != null ? List.copyOf(seatIds) : List.of();
    }

    public List<Long> getSeatIds() {
        return seatIds;
    }
}
