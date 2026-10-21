package com.dev.seatbooking.error;

import java.util.List;

public class OptimisticConflictException extends RuntimeException {

    private final List<Long> seatIds;

    public OptimisticConflictException(List<Long> seatIds) {
        super("Seat version changed", null, false, false);
        this.seatIds = List.copyOf(seatIds);
    }

    public List<Long> getSeatIds() {
        return seatIds;
    }
}
