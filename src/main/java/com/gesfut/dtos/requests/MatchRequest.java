package com.gesfut.dtos.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record MatchRequest(
        @NotNull(message = "El partido no puede estar vacío")
        Long matchId,
        @NotNull(message = "Los eventos son requeridos") List<@NotNull @Valid EventRequest> events,
        @Positive Long tiebreakWinnerId,
        // Revision critica #9: definicion por penales (opcional; solo cruces de playoffs empatados).
        @jakarta.validation.constraints.PositiveOrZero Integer penaltiesHome,
        @jakarta.validation.constraints.PositiveOrZero Integer penaltiesAway
) {
    public MatchRequest(Long matchId, List<EventRequest> events, Long tiebreakWinnerId) {
        this(matchId, events, tiebreakWinnerId, null, null);
    }

    /** Ganador del desempate: el de los penales si se cargaron, si no el elegido. */
    public Long effectiveTiebreakWinner(Long homeId, Long awayId) {
        if (penaltiesHome != null && penaltiesAway != null && !penaltiesHome.equals(penaltiesAway))
            return penaltiesHome > penaltiesAway ? homeId : awayId;
        return tiebreakWinnerId;
    }
}
