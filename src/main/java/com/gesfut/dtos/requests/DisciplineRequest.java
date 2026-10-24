package com.gesfut.dtos.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Revision critica #4: reglas de sancion del torneo. */
public record DisciplineRequest(
        @NotNull(message = "Indic\u00e1 cu\u00e1ntas amarillas suspenden.") @Min(value = 1, message = "M\u00ednimo 1 amarilla.") @Max(value = 20, message = "M\u00e1ximo 20 amarillas.") Integer yellowCardsToSuspend,
        @NotNull(message = "Indic\u00e1 cu\u00e1ntas fechas suspende una roja.") @Min(value = 1, message = "M\u00ednimo 1 fecha.") @Max(value = 10, message = "M\u00e1ximo 10 fechas.") Integer redCardSuspensionMatches
) { }
