package com.gesfut.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record MatchDayRequest (

        @NotBlank(message = "El código del torneo es requerido")
        String tournamentCode,

        @Size(min = 1, message = "Debe haber al menos 4 equipos")
        List<Long> teams,

        LocalDateTime startDate,

        Integer minutesPerMatch,

        Integer dayBetweenMatchDay
    ){}
