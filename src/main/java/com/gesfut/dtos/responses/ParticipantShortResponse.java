package com.gesfut.dtos.responses;

import java.io.Serial;
import java.util.Set;

public record ParticipantShortResponse(
        String nameTournament,
        Set<PlayerParticipantResponse> playerParticipants,
        Boolean isActive
)
{}
