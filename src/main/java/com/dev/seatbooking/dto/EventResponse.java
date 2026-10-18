package com.dev.seatbooking.dto;

import com.dev.seatbooking.entity.Event;

import java.time.Instant;

public record EventResponse(Long id, String name, String venue, Instant startsAt) {
    public static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getName(), e.getVenue(), e.getStartsAt());
    }
}
