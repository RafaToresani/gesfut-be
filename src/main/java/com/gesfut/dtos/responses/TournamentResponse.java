package com.gesfut.dtos.responses;

import java.time.LocalDate;
import java.util.Set;

public record TournamentResponse(
        String name,
        String code,
        LocalDate startDate,
        String manager,
        Set<ParticipantResponse> participants
) {
}
