package com.dev.seatbooking.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
