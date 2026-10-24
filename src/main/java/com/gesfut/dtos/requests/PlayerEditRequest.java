package com.gesfut.dtos.requests;

import jakarta.validation.constraints.*;

/** Editar jugador: datos del plantel (el historial de goles/tarjetas queda en el mismo jugador). */
public record PlayerEditRequest(
        @NotBlank(message = "Nombre es requerido")
        @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
        String name,
        @NotBlank(message = "Apellido es requerido")
        @Size(min = 3, max = 50, message = "El apellido debe tener entre 3 y 50 caracteres")
        String lastName,
        @NotNull(message = "Dorsal es requerido")
        @Min(value = 0, message = "El n\u00famero debe ser mayor a 0")
        @Max(value = 99, message = "El n\u00famero debe ser menor a 100")
        Integer number,
        @Pattern(regexp = "^$|^[0-9. ]{7,12}$", message = "El DNI debe tener 7 u 8 d\u00edgitos.")
        String dni
) { }
