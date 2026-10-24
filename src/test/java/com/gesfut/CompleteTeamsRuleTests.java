package com.gesfut;

import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import com.gesfut.services.impl.TournamentServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** C(b): regla de plantel minimo al generar el fixture (sin base de datos). */
class CompleteTeamsRuleTests {
    Player p(boolean captain, boolean gk, Boolean status) { return Player.builder().name("A").lastName("B").number(1).isCaptain(captain).isGoalKeeper(gk).status(status).build(); }
    Team team(String name, Player... ps) { return Team.builder().name(name).players(new HashSet<>(List.of(ps))).build(); }

    @Test void completoPasa() {
        assertDoesNotThrow(() -> TournamentServiceImpl.requireCompleteTeams(List.of(team("OK", p(true, false, true), p(false, true, null)))));
    }
    @Test void listaTodosLosIncompletos() {
        var e = assertThrows(IllegalArgumentException.class, () -> TournamentServiceImpl.requireCompleteTeams(List.of(
                team("Vacio"), team("SinArquero", p(true, false, true), p(false, false, true)),
                team("ConBaja", p(true, false, true), p(false, true, false)))));
        assertTrue(e.getMessage().contains("Vacio (falta: al menos 2 jugadores, capit\u00e1n, arquero)"), e.getMessage());
        assertTrue(e.getMessage().contains("SinArquero (falta: arquero)"), e.getMessage());
        assertTrue(e.getMessage().contains("ConBaja (falta: al menos 2 jugadores, arquero)"), e.getMessage());
    }
}
