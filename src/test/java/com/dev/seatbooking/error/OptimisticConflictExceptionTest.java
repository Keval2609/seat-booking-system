package com.dev.seatbooking.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OptimisticConflictExceptionTest {

    @Test
    @DisplayName("OptimisticConflictException has expected message and suppressed stack trace")
    void exceptionCharacteristics() {
        OptimisticConflictException ex = new OptimisticConflictException(42L);
        assertThat(ex.getMessage()).isEqualTo("Seat version changed");
        assertThat(ex.getStackTrace()).isEmpty();
        assertThat(ex.getSeatIds()).containsExactly(42L);
    }

    @Test
    @DisplayName("OptimisticConflictException supports list of seatIds")
    void exceptionWithList() {
        OptimisticConflictException ex = new OptimisticConflictException(List.of(10L, 11L));
        assertThat(ex.getSeatIds()).containsExactly(10L, 11L);
    }
}
