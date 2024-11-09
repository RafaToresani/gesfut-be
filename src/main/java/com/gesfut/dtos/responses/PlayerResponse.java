package com.gesfut.dtos.responses;

public record PlayerResponse(
        Long id,
        String name,
        String lastName,
        Integer number,
        Boolean isCaptain,
        Boolean isGoalKeeper
    )
{}
