package com.gesfut.dtos.responses;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record TournamentResponse(
        String name,
        String code,
        LocalDate startDate,
        String manager,
        Boolean isFinished,
        Boolean haveParticipants,
        List<ParticipantResponse> participants,
        List<MatchDayResponse> matchDays
) {
}
