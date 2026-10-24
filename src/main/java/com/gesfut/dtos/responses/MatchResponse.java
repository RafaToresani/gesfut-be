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
        String dateTime,
        String description,
        String mvpPlayer,
        Long vsMatchIdWhoWin,
        Long tiebreakWinnerId,
        // Plan 22/10 #9: false si el partido todavia no llego a su dia (no se puede cargar el resultado).
        Boolean canLoadResult,
        // Plan 22/10 #2: fecha libre (rival "Free" o dado de baja) y quien descansa.
        Boolean isBye,
        String restingTeam,
        // Revision critica #9: tanda de penales.
        Integer penaltiesHome,
        Integer penaltiesAway
        ) { }
