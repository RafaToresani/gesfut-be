package com.gesfut.dtos.responses;

import java.util.List;

public record ParticipantShortResponse(
        Long idParticipant,
        Long idTeam,
        String name,
        List<PlayerParticipantShortResponse> players
) {
}
