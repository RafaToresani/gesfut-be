package com.gesfut.dtos.responses;

import java.util.List;

public record ParticipantResponse(
        Long idParticipant,
        Long idTeam,
        String name,
        Boolean isActive,
        StatisticsResponse statistics,
        List<PlayerParticipantResponse> players
) {
}
