package com.dev.seatbooking.error;

public class OptimisticConflictException extends RuntimeException {
    public OptimisticConflictException() {
        super("Seat version changed", null, false, false);
    }
}
