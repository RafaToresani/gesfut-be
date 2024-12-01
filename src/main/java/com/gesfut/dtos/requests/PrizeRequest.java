package com.gesfut.dtos.requests;

import com.gesfut.models.tournament.EPrizeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PrizeRequest(
        @NotNull(message = "El tipo de premio no puede ser nulo")
        EPrizeType type,

        @NotBlank(message = "La descripción no puede estar vacía")
        String description,

        @NotNull(message = "La posición no puede ser nula")
        @Positive(message = "La posición debe ser un número positivo")
        Integer position
) {
}
