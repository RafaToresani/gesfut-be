package com.gesfut.dtos.responses;

import java.util.List;

public record MatchResponse(
        Long id,
        ParticipantResponse homeTeam,
        ParticipantResponse awayTeam,
        Integer numberOfMatchDay,
        Integer homeGoals,
        Integer awayGoals,
        List<EventResponse> events,
        Boolean isFinished
) { }
