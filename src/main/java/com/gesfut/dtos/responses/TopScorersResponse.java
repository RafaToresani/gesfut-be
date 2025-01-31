package com.gesfut.dtos.responses;

public record TopScorersResponse(
        String playerName,
        String teamName,
        Integer goals
) {
}
