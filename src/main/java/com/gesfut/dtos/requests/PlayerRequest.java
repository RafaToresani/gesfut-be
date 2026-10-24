package com.gesfut.dtos.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PlayerRequest(
        @Schema(description = "El nombre del jugador", example = "Cristian")
        @NotBlank(message = "Nombre es requerido")
        @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
        String name,
        @Schema(description = "El apellido del jugador", example = "Fabbiani")
        @NotBlank(message = "Apellido es requerido")
        @Size(min = 3, max = 50, message = "El apellido debe tener entre 3 y 50 caracteres")
        String lastName,
        @Schema(description = "Indica si es capitan", example = "true")
        @NotNull(message = "Capitan es requerido")
        Boolean isCaptain,
        @Schema(description = "Indica si es arquero", example = "false")
        @NotNull(message = "Arquero es requerido")
        Boolean isGoalKeeper,
        @Schema(description = "Dorsal del jugador", example = "10")
        @NotNull(message = "Dorsal es requerido")
        @Min(value = 0, message = "El número debe ser mayor a 0")
        @Max(value = 99, message = "El número debe ser menor a 100")
        Integer number,
        @Schema(description = "DNI opcional (7 u 8 digitos)", example = "30123456")
        @jakarta.validation.constraints.Pattern(regexp = "^$|^[0-9. ]{7,12}$", message = "El DNI debe tener 7 u 8 d\u00edgitos.")
        String dni
){
    public PlayerRequest(String name, String lastName, Boolean isCaptain, Boolean isGoalKeeper, Integer number) {
        this(name, lastName, isCaptain, isGoalKeeper, number, null);
    }
}
