package com.gesfut.config.security.dtos;

public record AuthResponse(
        String name,
        String lastName,
        String token,
        String role
) {
}
