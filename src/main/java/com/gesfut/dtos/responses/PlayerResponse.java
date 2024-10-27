package com.gesfut.dtos.responses;

import com.gesfut.models.tournament.PlayerParticipant;

import java.util.Set;

public record PlayerResponse(
        Long id,
        String name,
        String lastName,
        Integer number,
        Boolean isCaptain,
        Boolean isGoalKeeper
    )
{}
