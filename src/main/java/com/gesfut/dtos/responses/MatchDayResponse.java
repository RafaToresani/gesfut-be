package com.gesfut.dtos.responses;

import java.util.List;

public record MatchDayResponse(
        Integer numberOfMatchDay,
        List<MatchResponse> matches
) {
}
