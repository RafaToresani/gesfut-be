package com.gesfut.services.impl;

import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.TournamentParticipant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Respuesta 1: DNI opcional; con DNI cargado un jugador no puede estar en dos equipos del mismo torneo. */
class DniRulesTests {
    Team a = Team.builder().id(1L).name("TEST A").build(), b = Team.builder().id(2L).name("TEST B").build();
    PlayerParticipant in(Team team, String dni) {
        return PlayerParticipant.builder().player(Player.builder().name("Juan").lastName("Perez").dni(dni).build())
                .tournamentParticipant(TournamentParticipant.builder().team(team).build()).build();
    }

    @Test void normalizaYValida() {
        assertEquals("30123456", DniRules.normalize("30.123.456"));
        assertNull(DniRules.normalize("  "));
        assertNull(DniRules.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> DniRules.normalize("123"));
    }

    @Test void mismoDniEnOtroEquipoSeBloquea() {
        Player p = Player.builder().name("Juan").lastName("Perez").dni("30123456").build();
        var e = assertThrows(IllegalArgumentException.class, () -> DniRules.requireNotInOtherTeam(List.of(in(a, "30123456")), p, b));
        assertTrue(e.getMessage().contains("ya juega este torneo en TEST A"));
    }

    @Test void mismoEquipoOSinDniNoSeBloquea() {
        Player p = Player.builder().name("Juan").lastName("Perez").dni("30123456").build();
        assertDoesNotThrow(() -> DniRules.requireNotInOtherTeam(List.of(in(a, "30123456")), p, a));
        Player sinDni = Player.builder().name("Juan").lastName("Perez").build();
        assertDoesNotThrow(() -> DniRules.requireNotInOtherTeam(List.of(in(a, null)), sinDni, b));
    }
}
