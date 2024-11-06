package com.gesfut.dtos.responses;

public record PlayerParticipantResponse(
        Long id,
        Boolean isSuspended,
        Integer goals,
        Integer redCards,
        Integer yellowCards,
        Integer isMvp,
        Long playerId,
        String playerName,
        Integer matchesPlayed
) {}
