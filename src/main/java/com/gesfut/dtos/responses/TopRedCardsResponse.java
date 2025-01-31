package com.gesfut.dtos.responses;

public record TopRedCardsResponse(
        String playerName,
        String teamName,
        Integer redCards
) {
}
