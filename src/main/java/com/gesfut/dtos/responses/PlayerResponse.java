package com.gesfut.dtos.responses;

public record PlayerResponse(
        String name,
        String lastName,
        Integer number,
        Boolean isCaptain,
        Boolean isGoalKeeper,
        Boolean isSuspended
    )
{



}
