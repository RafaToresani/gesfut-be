package com.gesfut.dtos.responses;

public record MatchResponse(
        String homeTeam,
        String awayTeam,
        Integer numberOfMatchDay,
        Integer homeGoals,
        Integer awayGoals
) { }
