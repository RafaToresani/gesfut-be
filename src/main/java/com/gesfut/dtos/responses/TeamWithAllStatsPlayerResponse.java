package com.gesfut.dtos.responses;

import java.util.Set;

public record TeamWithAllStatsPlayerResponse(
        Long idTeam,
        String name,
        Boolean status,
        Set<PlayerParticipantResponse> playerParticipants
) {}
