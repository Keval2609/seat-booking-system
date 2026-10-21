package com.dev.seatbooking.strategy;

import com.dev.seatbooking.entity.Seat;
import com.dev.seatbooking.entity.SeatStatus;
import com.dev.seatbooking.error.OptimisticConflictException;
import com.dev.seatbooking.error.SeatUnavailableException;
import com.dev.seatbooking.error.ValidationFailedException;
import com.dev.seatbooking.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OptimisticSeatLockingStrategyTest {

    private SeatRepository seatRepository;
    private OptimisticSeatLockingStrategy strategy;

    @BeforeEach
    void setUp() {
        seatRepository = mock(SeatRepository.class);
        strategy = new OptimisticSeatLockingStrategy(seatRepository);
    }

    private Seat makeSeat(Long id, Long eventId, SeatStatus status, Long version) {
        Seat seat = new Seat(eventId, "A" + id, 1000, status);
        ReflectionTestUtils.setField(seat, "id", id);
        ReflectionTestUtils.setField(seat, "version", version);
        return seat;
    }

    @Test
    @DisplayName("Throws ValidationFailedException if seats count does not match sortedSeatIds")
    void seatNotFoundThrowsValidationFailed() {
        when(seatRepository.findForEvent(1L, List.of(10L, 11L)))
                .thenReturn(List.of(makeSeat(10L, 1L, SeatStatus.AVAILABLE, 0L)));

        assertThatThrownBy(() -> strategy.acquire(100L, 1L, List.of(10L, 11L)))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessage("Seat not found in event");
    }

    @Test
    @DisplayName("Throws SeatUnavailableException if any seat is not AVAILABLE")
    void blockedSeatThrowsSeatUnavailable() {
        Seat s1 = makeSeat(10L, 1L, SeatStatus.AVAILABLE, 0L);
        Seat s2 = makeSeat(11L, 1L, SeatStatus.HELD, 1L);
        when(seatRepository.findForEvent(1L, List.of(10L, 11L))).thenReturn(List.of(s1, s2));

        assertThatThrownBy(() -> strategy.acquire(100L, 1L, List.of(10L, 11L)))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    @DisplayName("Throws OptimisticConflictException if compareAndHold returns != 1")
    void casFailureThrowsOptimisticConflict() {
        Seat s1 = makeSeat(10L, 1L, SeatStatus.AVAILABLE, 0L);
        when(seatRepository.findForEvent(1L, List.of(10L))).thenReturn(List.of(s1));
        when(seatRepository.compareAndHold(10L, 0L, 100L)).thenReturn(0);

        assertThatThrownBy(() -> strategy.acquire(100L, 1L, List.of(10L)))
                .isInstanceOf(OptimisticConflictException.class)
                .satisfies(e -> assertThat(((OptimisticConflictException) e).getSeatIds()).containsExactly(10L));
    }

    @Test
    @DisplayName("Acquires successfully when all seats CAS succeed")
    void successfulAcquire() {
        Seat s1 = makeSeat(10L, 1L, SeatStatus.AVAILABLE, 0L);
        Seat s2 = makeSeat(11L, 1L, SeatStatus.AVAILABLE, 2L);
        when(seatRepository.findForEvent(1L, List.of(10L, 11L))).thenReturn(List.of(s1, s2));
        when(seatRepository.compareAndHold(10L, 0L, 100L)).thenReturn(1);
        when(seatRepository.compareAndHold(11L, 2L, 100L)).thenReturn(1);

        strategy.acquire(100L, 1L, List.of(10L, 11L));

        verify(seatRepository).compareAndHold(10L, 0L, 100L);
        verify(seatRepository).compareAndHold(11L, 2L, 100L);
    }
}
