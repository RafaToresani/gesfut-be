package com.gesfut.dtos.requests;

import com.gesfut.models.matchDay.EEventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EventRequest(
        @NotNull(message = "El evento debe tener un jugador")
        Long playerParticipantId, //tabla jugadorportonreo_id  der
        @NotNull(message = "El tipo de evento es requerido") EEventType type,
        @NotNull(message = "La cantidad es requerida")
        @Positive(message = "La cantidad debe ser positiva")
        Integer quantity
) {
}
