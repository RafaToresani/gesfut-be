package com.gesfut.dtos.requests;

import com.gesfut.models.matchDay.EEventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record EventRequest(
        @NotBlank(message = "El evento debe tener un jugador")
        Long playerParticipantId, //tabla jugadorportonreo_id  der
        EEventType type,
        @Min(value = 0, message = "La cantidad no puede ser negativa")
        Integer quantity
) {
}
