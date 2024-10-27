package com.gesfut.dtos.requests;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MatchRequest(
        @NotNull(message = "El partido no puede estar vacío")
        Long matchId,
        List<EventRequest> events
) {
}
