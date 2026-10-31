package com.dev.seatbooking.strategy;

import com.dev.seatbooking.entity.Seat;
import com.dev.seatbooking.entity.SeatStatus;
import com.dev.seatbooking.error.SeatUnavailableException;
import com.dev.seatbooking.error.ValidationFailedException;
import com.dev.seatbooking.repository.SeatRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@ConditionalOnProperty(name = "booking.locking-strategy", havingValue = "PESSIMISTIC")
public class PessimisticSeatLockingStrategy implements SeatLockingStrategy {

    private final SeatRepository seats;

    public PessimisticSeatLockingStrategy(SeatRepository seats) {
        this.seats = seats;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(Long holdId, Long eventId, List<Long> sortedSeatIds) {
        List<Seat> locked = seats.lockForEvent(eventId, sortedSeatIds);
        if (locked.size() != sortedSeatIds.size()) {
            throw new ValidationFailedException("Seat not found in event");
        }
        List<Long> blocked = locked.stream()
                .filter(s -> s.getStatus() != SeatStatus.AVAILABLE)
                .map(Seat::getId)
                .toList();
        if (!blocked.isEmpty()) {
            throw new SeatUnavailableException(blocked);
        }
        if (seats.claimAvailable(holdId, sortedSeatIds) != sortedSeatIds.size()) {
            throw new SeatUnavailableException(sortedSeatIds);
        }
    }
}
