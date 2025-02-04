package com.gesfut.dtos.responses;

import com.gesfut.models.matchDay.EEventType;

public record EventResponse(
        Long id,
        Integer quantity,
        EEventType type,
        String playerName,
        String teamName,
        Long idPlayerParticipant
) {
}
