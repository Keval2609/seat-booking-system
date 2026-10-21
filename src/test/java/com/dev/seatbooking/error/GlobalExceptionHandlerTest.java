package com.dev.seatbooking.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class TestController {
        @GetMapping("/test/required-header")
        public String requiredHeader(@RequestHeader("X-Required") String header) {
            return "ok: " + header;
        }

        @GetMapping("/test/optimistic-conflict")
        public void optimisticConflict() {
            throw new OptimisticConflictException(List.of(42L));
        }

        @GetMapping("/test/pessimistic-conflict")
        public void pessimisticConflict() {
            throw new CannotAcquireLockException("Lock timeout");
        }

        @GetMapping("/test/deadlock")
        public void deadlock() {
            SQLException sqlEx = new SQLException("deadlock detected", "40P01");
            throw new CannotAcquireLockException("Deadlock detected", sqlEx);
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("Missing request header returns 400 VALIDATION_FAILED, not 500")
    void missingHeader_returns400ValidationFailed() throws Exception {
        mockMvc.perform(get("/test/required-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.message", is("Request rejected")));
    }

    @Test
    @DisplayName("OptimisticConflictException returns 409 SEAT_UNAVAILABLE with seatIds")
    void optimisticConflict_returns409WithSeatIds() throws Exception {
        mockMvc.perform(get("/test/optimistic-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("SEAT_UNAVAILABLE")))
                .andExpect(jsonPath("$.message", is("One or more seats are not available")))
                .andExpect(jsonPath("$.seatIds", contains(42)));
    }

    @Test
    @DisplayName("Pessimistic lock conflict (non-deadlock) returns 409 SEAT_UNAVAILABLE")
    void pessimisticLockConflict_returns409() throws Exception {
        mockMvc.perform(get("/test/pessimistic-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("SEAT_UNAVAILABLE")))
                .andExpect(jsonPath("$.message", is("One or more seats are not available")));
    }

    @Test
    @DisplayName("PostgreSQL deadlock (SQLState 40P01) returns 500 DEADLOCK")
    void deadlock_returns500Deadlock() throws Exception {
        mockMvc.perform(get("/test/deadlock"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code", is("DEADLOCK")))
                .andExpect(jsonPath("$.message", is("Database deadlock detected")));
    }
}
