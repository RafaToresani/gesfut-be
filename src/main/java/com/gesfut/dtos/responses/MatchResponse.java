package com.gesfut.dtos.responses;

import java.util.List;

public record MatchResponse(
        Long id,
        String homeTeam,
        String awayTeam,
        Integer numberOfMatchDay,
        Integer homeGoals,
        Integer awayGoals,
        List<EventResponse> events
) { }
