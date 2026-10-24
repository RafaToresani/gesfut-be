package com.gesfut.dtos.responses;

import java.util.Set;

public record ParticipantResponse(
        Long idParticipant,
        Long idTeam,
        String name,
        Boolean isActive,
        StatisticsResponse statistics,
        Set<PlayerParticipantResponse> playerParticipants,
        // Revision critica #14: color del equipo para la vista publica (sin pedir /teams/{id}).
        String color
) {
    public ParticipantResponse(Long idParticipant, Long idTeam, String name, Boolean isActive, StatisticsResponse statistics, Set<PlayerParticipantResponse> playerParticipants) {
        this(idParticipant, idTeam, name, isActive, statistics, playerParticipants, null);
    }
}
