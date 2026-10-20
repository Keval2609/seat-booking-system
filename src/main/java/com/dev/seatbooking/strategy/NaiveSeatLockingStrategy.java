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
@ConditionalOnProperty(name = "booking.locking-strategy", havingValue = "NAIVE", matchIfMissing = true)
public class NaiveSeatLockingStrategy implements SeatLockingStrategy {

    private final SeatRepository seatRepository;

    public NaiveSeatLockingStrategy(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(Long holdId, Long eventId, List<Long> sortedSeatIds) {
        List<Seat> seats = seatRepository.findForEvent(eventId, sortedSeatIds);
        if (seats.size() != sortedSeatIds.size()) {
            throw new ValidationFailedException("Seat not found in event");
        }
        List<Long> blocked = seats.stream()
                .filter(s -> s.getStatus() != SeatStatus.AVAILABLE)
                .map(Seat::getId)
                .toList();
        if (!blocked.isEmpty()) {
            throw new SeatUnavailableException(blocked);
        }

        afterRead();

        int updated = seatRepository.blindHold(holdId, sortedSeatIds);
        if (updated != sortedSeatIds.size()) {
            throw new SeatUnavailableException(sortedSeatIds);
        }
    }

    protected void afterRead() {
        // Test hook overridden by CyclicBarrier in T8
    }
}
