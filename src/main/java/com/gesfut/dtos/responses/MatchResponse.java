package com.gesfut.dtos.responses;

public record MatchResponse(
        Long id,
        String homeTeam,
        String awayTeam,
        Integer numberOfMatchDay,
        Integer homeGoals,
        Integer awayGoals
) { }
