package com.dev.seatbooking.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OptimisticConflictExceptionTest {

    @Test
    @DisplayName("OptimisticConflictException has expected message and suppressed stack trace")
    void exceptionCharacteristics() {
        OptimisticConflictException ex = new OptimisticConflictException();
        assertThat(ex.getMessage()).isEqualTo("Seat version changed");
        assertThat(ex.getStackTrace()).isEmpty();
    }
}
