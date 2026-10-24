package com.gesfut.dtos.responses;

import java.util.List;

/** Plan 22/10 #2: que pasa al tocar "Eliminar equipo". action: DELETE (nunca jugo) o DISABLE (tiene historial). */
public record TeamDeletionResponse(
        Long teamId,
        String teamName,
        String action,
        List<String> ongoingTournaments,
        /** Torneos sin partidos jugados: se quita al equipo y se regenera el fixture. */
        List<String> regeneratedTournaments,
        String blockedReason,
        String message
) { }
