package com.dev.seatbooking.strategy;

import com.dev.seatbooking.entity.Seat;
import com.dev.seatbooking.entity.SeatStatus;
import com.dev.seatbooking.error.OptimisticConflictException;
import com.dev.seatbooking.error.SeatUnavailableException;
import com.dev.seatbooking.error.ValidationFailedException;
import com.dev.seatbooking.repository.SeatRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@ConditionalOnProperty(name = "booking.locking-strategy", havingValue = "OPTIMISTIC")
public class OptimisticSeatLockingStrategy implements SeatLockingStrategy {

    private final SeatRepository seatRepository;

    public OptimisticSeatLockingStrategy(SeatRepository seatRepository) {
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
        for (Seat seat : seats) {
            if (seatRepository.compareAndHold(seat.getId(), seat.getVersion(), holdId) != 1) {
                throw new OptimisticConflictException();
            }
        }
    }
}
