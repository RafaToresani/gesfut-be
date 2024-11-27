package com.gesfut.dtos.responses;

import java.io.Serial;
import java.util.Set;

public record ParticipantShortResponse(
        String nameTournament,
        String codeTournament,
        Long idParticipant,
        Set<PlayerParticipantResponse> playerParticipants,
        Boolean isActive
)
{}
