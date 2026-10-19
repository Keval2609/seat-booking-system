package com.dev.seatbooking.error;

import java.util.List;

public class SeatUnavailableException extends RuntimeException {
    private final List<Long> seatIds;

    public SeatUnavailableException(List<Long> seatIds) {
        super("One or more seats are not available");
        this.seatIds = seatIds;
    }

    public List<Long> getSeatIds() {
        return seatIds;
    }
}
