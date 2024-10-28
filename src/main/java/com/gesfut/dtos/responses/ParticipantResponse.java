package com.gesfut.dtos.responses;

public record ParticipantResponse(
        Long idParticipant,
        Long idTeam,
        String name,
        Boolean isActive,
        StatisticsResponse statistics
) {
}
