package com.gesfut.dtos.responses;

public record PlayerParticipantResponse(
        Long id,
        Integer shirtNumber,
        Boolean isSuspended,
        Integer goals,
        Integer redCards,
        Integer yellowCards,
        Integer isMvp,
        Long playerId,
        String playerName,
        String playerLastName,
        Integer matchesPlayed
) {}
