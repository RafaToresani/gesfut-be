package com.gesfut.dtos.responses;

import java.util.List;

public record MatchDayResponse(
        Long idMatchDay,
        Integer numberOfMatchDay,
        Boolean isFinished,
        List<MatchResponse> matches
) {
}
