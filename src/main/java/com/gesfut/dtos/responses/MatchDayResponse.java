package com.gesfut.dtos.responses;

import java.util.List;

public record MatchDayResponse(
        Long idMatchDay,
        Integer numberOfMatchDay,
        Boolean isFinished,
        String mvpPlayer,
        List<MatchResponse> matches,
        Boolean isPlayOff
) {
}
