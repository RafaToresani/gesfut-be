package com.gesfut.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

public record MatchDayRequest (

        @NotBlank(message = "El código del torneo es requerido")
        String tournamentCode,

        @NotNull(message = "Los equipos son requeridos")
        @Size(min = 1, message = "Debe seleccionar al menos un equipo")
        List<@NotNull @Positive Long> teams,

        LocalDateTime startDate,

        @Positive(message = "La duración debe ser positiva") Integer minutesPerMatch,

        @Positive(message = "El intervalo entre fechas tiene que ser de al menos 1 d\u00eda.") Integer dayBetweenMatchDay
    ){}
