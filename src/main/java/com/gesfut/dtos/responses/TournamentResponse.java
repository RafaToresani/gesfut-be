package com.gesfut.dtos.responses;

import java.time.LocalDate;

public record TournamentResponse(
        String name,
        String code,
        LocalDate startDate,
        String manager
) {
}
