package com.dev.seatbooking.strategy;

import com.dev.seatbooking.entity.Seat;
import com.dev.seatbooking.entity.SeatStatus;
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

class PessimisticSeatLockingStrategyTest {

    private SeatRepository seatRepository;
    private PessimisticSeatLockingStrategy strategy;

    @BeforeEach
    void setUp() {
        seatRepository = mock(SeatRepository.class);
        strategy = new PessimisticSeatLockingStrategy(seatRepository);
    }

    private Seat makeSeat(Long id, Long eventId, SeatStatus status) {
        Seat seat = new Seat(eventId, "A" + id, 1000, status);
        ReflectionTestUtils.setField(seat, "id", id);
        return seat;
    }

    @Test
    @DisplayName("Throws ValidationFailedException if locked seats count does not match sortedSeatIds")
    void seatNotFoundThrowsValidationFailed() {
        when(seatRepository.lockForEvent(1L, List.of(10L, 11L)))
                .thenReturn(List.of(makeSeat(10L, 1L, SeatStatus.AVAILABLE)));

        assertThatThrownBy(() -> strategy.acquire(100L, 1L, List.of(10L, 11L)))
                .isInstanceOf(ValidationFailedException.class)
                .hasMessage("Seat not found in event");
    }

    @Test
    @DisplayName("Throws SeatUnavailableException if any seat is not AVAILABLE")
    void blockedSeatThrowsSeatUnavailable() {
        Seat s1 = makeSeat(10L, 1L, SeatStatus.AVAILABLE);
        Seat s2 = makeSeat(11L, 1L, SeatStatus.HELD);
        when(seatRepository.lockForEvent(1L, List.of(10L, 11L))).thenReturn(List.of(s1, s2));

        assertThatThrownBy(() -> strategy.acquire(100L, 1L, List.of(10L, 11L)))
                .isInstanceOf(SeatUnavailableException.class)
                .satisfies(e -> assertThat(((SeatUnavailableException) e).getSeatIds()).containsExactly(11L));
    }

    @Test
    @DisplayName("Throws SeatUnavailableException if claimAvailable updates fewer rows than requested")
    void claimMismatchThrowsSeatUnavailable() {
        Seat s1 = makeSeat(10L, 1L, SeatStatus.AVAILABLE);
        when(seatRepository.lockForEvent(1L, List.of(10L))).thenReturn(List.of(s1));
        when(seatRepository.claimAvailable(100L, List.of(10L))).thenReturn(0);

        assertThatThrownBy(() -> strategy.acquire(100L, 1L, List.of(10L)))
                .isInstanceOf(SeatUnavailableException.class)
                .satisfies(e -> assertThat(((SeatUnavailableException) e).getSeatIds()).containsExactly(10L));
    }

    @Test
    @DisplayName("Acquires successfully when all seats are AVAILABLE and claimAvailable succeeds")
    void successfulAcquire() {
        Seat s1 = makeSeat(10L, 1L, SeatStatus.AVAILABLE);
        Seat s2 = makeSeat(11L, 1L, SeatStatus.AVAILABLE);
        when(seatRepository.lockForEvent(1L, List.of(10L, 11L))).thenReturn(List.of(s1, s2));
        when(seatRepository.claimAvailable(100L, List.of(10L, 11L))).thenReturn(2);

        strategy.acquire(100L, 1L, List.of(10L, 11L));

        verify(seatRepository).lockForEvent(1L, List.of(10L, 11L));
        verify(seatRepository).claimAvailable(100L, List.of(10L, 11L));
    }
}
