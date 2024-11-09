package com.gesfut.dtos.responses;

import java.util.Set;

public record ParticipantResponse(
        Long idParticipant,
        Long idTeam,
        String name,
        Boolean isActive,
        StatisticsResponse statistics,
        Set<PlayerParticipantResponse> playerParticipants
) {}
