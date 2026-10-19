package com.dev.seatbooking.strategy;

import java.util.List;

public interface SeatLockingStrategy {
    void acquire(Long holdId, Long eventId, List<Long> sortedSeatIds);
}
