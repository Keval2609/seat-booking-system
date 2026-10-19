package com.dev.seatbooking.error;

public class EventClosedException extends RuntimeException {
    public EventClosedException() {
        super("Event has started");
    }
}
