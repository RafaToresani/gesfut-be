package com.gesfut.dtos.responses;

public record StatisticsResponse(
        Integer points,
        Integer matchesPlayed,
        Integer wins,
        Integer draws,
        Integer losses,
        Integer goalsFor,
        Integer goalsAgainst
) {
}
