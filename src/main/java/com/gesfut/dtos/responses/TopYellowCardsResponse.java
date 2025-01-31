package com.gesfut.dtos.responses;

public record TopYellowCardsResponse(
        String playerName,
        String teamName,
        Integer yellowCards
) {
}
