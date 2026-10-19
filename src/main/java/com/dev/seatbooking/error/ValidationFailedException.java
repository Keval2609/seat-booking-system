package com.dev.seatbooking.error;

public class ValidationFailedException extends RuntimeException {
    public ValidationFailedException(String message) {
        super(message);
    }
}
