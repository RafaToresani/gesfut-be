package com.gesfut.dtos.responses;

import java.time.LocalDate;

public record TournamentShortResponse(
        String name,
        String code,
        LocalDate startDate,
        Boolean isFinished,
        Boolean isActive,
        Boolean haveParticipants
) {
}
