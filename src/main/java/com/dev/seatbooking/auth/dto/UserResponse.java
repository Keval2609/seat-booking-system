package com.dev.seatbooking.auth.dto;

import com.dev.seatbooking.entity.Role;

public record UserResponse(Long id, String email, Role role) {}
