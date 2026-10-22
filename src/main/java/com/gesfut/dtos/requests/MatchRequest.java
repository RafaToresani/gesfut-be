package com.gesfut.dtos.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record MatchRequest(
        @NotNull(message = "El partido no puede estar vacío")
        Long matchId,
        @NotNull(message = "Los eventos son requeridos") List<@NotNull @Valid EventRequest> events,
        @Positive Long tiebreakWinnerId
) {
}
