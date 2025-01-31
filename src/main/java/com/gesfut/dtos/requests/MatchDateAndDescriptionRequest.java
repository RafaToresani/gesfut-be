package com.gesfut.dtos.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record MatchDateAndDescriptionRequest(
        @NotNull(message = "La fecha y hora no puede ser nula.")
        LocalDateTime localDateTime,

        @NotNull(message = "La descripción no puede ser nula.")
        @Size(max = 255, message = "La descripción no puede tener más de 255 caracteres.")
        String description
) {
}
