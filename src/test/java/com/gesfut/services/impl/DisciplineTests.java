package com.gesfut.services.impl;

import com.gesfut.dtos.requests.EventRequest;
import com.gesfut.dtos.requests.MatchRequest;
import com.gesfut.models.matchDay.*;
import com.gesfut.models.team.Player;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.repositories.EventRepository;
import com.gesfut.repositories.PlayerParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Revision critica #3/#4: sanciones cronologicas y jugador suspendido bloqueado al cargar resultado. */
class DisciplineTests {
    Tournament t; TournamentParticipant home, away; PlayerParticipant juan; long ids = 1;
    Map<Long, List<Event>> events = new HashMap<>();
    EventRepository eventRepo = mock(EventRepository.class);
    PlayerParticipantRepository ppRepo = mock(PlayerParticipantRepository.class);

    @BeforeEach void setUp() {
        t = Tournament.builder().id(1L).name("TEST").teams(new HashSet<>()).matchDays(new HashSet<>()).build();
        home = part("TEST Local"); away = part("TEST Visita");
        juan = PlayerParticipant.builder().id(ids++).tournamentParticipant(home).player(Player.builder().id(ids++).name("Juan").lastName("Perez").build()).build();
        home.getPlayerParticipants().add(juan);
        when(eventRepo.findAllByMatchId(any())).thenAnswer(inv -> events.getOrDefault(inv.getArgument(0), List.of()));
        when(ppRepo.findById(juan.getId())).thenReturn(Optional.of(juan));
    }
    TournamentParticipant part(String n) {
        var p = TournamentParticipant.builder().id(ids++).tournament(t).team(Team.builder().id(ids++).name(n).build()).playerParticipants(new HashSet<>()).build();
        t.getTeams().add(p); return p;
    }
    Match match(int day, boolean finished) {
        MatchDay d = MatchDay.builder().id(ids++).numberOfMatchDay(day).isFinished(finished).isPlayOff(false).tournament(t).matches(new HashSet<>()).build();
        Match m = Match.builder().id(ids++).homeTeam(home).awayTeam(away).isFinished(finished).goalsHomeTeam(0).goalsAwayTeam(0).matchDay(d).build();
        d.getMatches().add(m); t.getMatchDays().add(d); return m;
    }
    void card(Match m, EEventType type) { events.computeIfAbsent(m.getId(), k -> new ArrayList<>()).add(Event.builder().id(ids++).type(type).quantity(1).playerParticipant(juan).match(m).build()); }
    DisciplineRules.State state(Match stopBefore, int yellows, int reds) {
        var list = DisciplineRules.chronological(t, stopBefore);
        return DisciplineRules.compute(t, list, events, yellows, reds).get(juan.getId());
    }

    @Test void rojaSuspendeElSiguientePartidoYSeCumple() {
        Match m1 = match(0, true), m2 = match(1, true), m3 = match(2, false);
        card(m1, EEventType.RED_CARD);
        assertTrue(state(m2, 5, 1).suspended(), "suspendido para la fecha 2");
        assertFalse(state(m3, 5, 1).suspended(), "cumplio en la fecha 2");
        assertTrue(state(m3, 5, 2).suspended(), "con 2 fechas por roja sigue suspendido");
    }

    @Test void amarillasAcumuladas() {
        Match m1 = match(0, true), m2 = match(1, true), m3 = match(2, false);
        card(m1, EEventType.YELLOW_CARD); card(m2, EEventType.YELLOW_CARD);
        assertTrue(state(m3, 2, 1).suspended());
        assertFalse(state(m3, 5, 1).suspended());
        assertEquals(2, state(m3, 5, 1).yellows());
    }

    @Test void suspendidoNoPuedeTenerEventos() {
        Match m1 = match(0, true), m2 = match(1, false);
        card(m1, EEventType.RED_CARD);
        MatchServiceImpl service = new MatchServiceImpl();
        ReflectionTestUtils.setField(service, "eventRepository", eventRepo);
        ReflectionTestUtils.setField(service, "playerParticipantRepository", ppRepo);
        var req = new MatchRequest(m2.getId(), List.of(new EventRequest(juan.getId(), EEventType.GOAL, 1)), null);
        var e = assertThrows(IllegalArgumentException.class, () -> service.checkSuspendedPlayers(req, m2));
        assertTrue(e.getMessage().contains("Juan Perez est\u00e1 suspendido"));
        var ok = new MatchRequest(m1.getId(), List.of(new EventRequest(juan.getId(), EEventType.GOAL, 1)), null);
        assertDoesNotThrow(() -> service.checkSuspendedPlayers(ok, m1));
    }
}
