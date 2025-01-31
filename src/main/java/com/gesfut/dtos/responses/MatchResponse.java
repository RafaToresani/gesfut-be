package com.gesfut.dtos.responses;

import java.time.LocalDateTime;
import java.util.List;

public record MatchResponse(
        Long id,
        String homeTeam,
        String awayTeam,
        Integer numberOfMatchDay,
        Integer homeGoals,
        Integer awayGoals,
        List<EventResponse> events,
        Boolean isFinished,
        LocalDateTime dateTime,
        String description
) { }
