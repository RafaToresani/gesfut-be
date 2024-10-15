package com.gesfut.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record TeamRequest(

        @NotBlank(message = "Nombre es requerido")
        @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
        String name,

        @NotBlank(message = "Color es requerido")
        String color,

        @Size(min = 1, message = "Debe tener al menos un jugador")
        Set<PlayerRequest> players
) {
}
