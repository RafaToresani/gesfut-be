package com.gesfut.dtos.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record TeamRequest(

        @Schema(description = "El nombre del equipo", example = "NO FABBIANI")
        @NotBlank(message = "Nombre es requerido")
        @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
        String name,

        @Schema(description = "El color de la camiseta", example = "#FFFFFF")
        @NotBlank(message = "Color es requerido")
        String color,

        // C(b): se puede crear solo con nombre; el plantel m\u00ednimo se exige al generar el fixture.
        Set<PlayerRequest> players
) {
}
