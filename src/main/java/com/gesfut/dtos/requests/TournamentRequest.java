package com.gesfut.dtos.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TournamentRequest(
        @Schema(description = "El nombre que va a tener el torneo.", example = "Torneo 'Chiqui' Tapia - Padecela")
        @NotBlank(message = "Nombre es requerido")
        @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
        String name
) {
}
