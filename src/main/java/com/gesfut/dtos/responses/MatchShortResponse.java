package com.gesfut.dtos.responses;

import java.util.List;

public record MatchShortResponse(
        Long id,
        ParticipantShortResponse homeTeam,
        ParticipantShortResponse awayTeam,
        Integer numberOfMatchDay,
        List<EventResponse> events
) {
}
