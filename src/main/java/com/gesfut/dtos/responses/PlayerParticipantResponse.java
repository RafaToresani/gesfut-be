package com.gesfut.dtos.responses;

public record PlayerParticipantResponse(
        Long playerParticipantId,
        PlayerResponse player,
        Boolean isSuspended,
        Integer goals,
        Integer yellow_cards,
        Integer red_cards,
        Integer mvps
) {
}
