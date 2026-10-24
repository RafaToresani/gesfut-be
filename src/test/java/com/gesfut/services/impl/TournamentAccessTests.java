package com.gesfut.services.impl;

import com.gesfut.models.tournament.Tournament;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

/** Revision critica #2 y #13: finalizado o archivado = solo lectura. */
class TournamentAccessTests {
    TournamentAccess access = new TournamentAccess();

    @Test void archivadoEsSoloLectura() {
        var e = assertThrows(ResponseStatusException.class, () -> access.requireOpen(Tournament.builder().isActive(false).build()));
        assertTrue(e.getReason().contains("archivado"));
    }

    @Test void finalizadoEsSoloLectura() {
        assertThrows(ResponseStatusException.class, () -> access.requireOpen(Tournament.builder().isFinished(true).isActive(true).build()));
    }

    @Test void activoSePuedeEditar() {
        assertDoesNotThrow(() -> access.requireOpen(Tournament.builder().isActive(true).isFinished(false).build()));
        assertDoesNotThrow(() -> access.requireOpen(Tournament.builder().build()));
    }
}
