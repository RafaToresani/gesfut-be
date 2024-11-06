package com.gesfut.dtos.responses;

public record PlayerParticipantShortResponse(
        Long playerParticipantId,
        String name,
        String lastName
) {
}
