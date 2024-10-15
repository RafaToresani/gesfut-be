package com.gesfut.dtos.responses;

import java.util.Set;

public record TeamResponse(
        Long id,
        String name,
        String color,
        Set<PlayerResponse> players
) {
}
