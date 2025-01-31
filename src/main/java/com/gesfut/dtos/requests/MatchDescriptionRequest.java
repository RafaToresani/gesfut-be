package com.gesfut.dtos.requests;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

public record MatchDescriptionRequest(
        @NotNull(message = "La descripción no puede ser nula.")
        @Length(max = 255, message = "La descripción no puede tener más de 255 caracteres")
        String description
) {
}
