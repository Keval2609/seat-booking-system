package com.dev.seatbooking.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<Long> seatIds) {
    public ErrorResponse(String code, String message) {
        this(code, message, null);
    }
}

