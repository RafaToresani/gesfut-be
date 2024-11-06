package com.gesfut.dtos.responses;

import java.util.List;

public record MatchDayShortResponse(
        Long idMatchDay,
        Integer numberOfMatchDay,
        Boolean isFinished,
        List<MatchShortResponse> matches
) {
}
