package com.gesfut.dtos.responses;

public record ParticipantResponse(
        Long idTeam,
        String name,
        StatisticsResponse statistics
) {
}
