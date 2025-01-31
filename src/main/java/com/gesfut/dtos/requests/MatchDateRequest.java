package com.gesfut.dtos.requests;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record MatchDateRequest(
        @NotNull(message = "La fecha y hora no puede ser nula.")
        LocalDateTime localDateTime

) {
}
