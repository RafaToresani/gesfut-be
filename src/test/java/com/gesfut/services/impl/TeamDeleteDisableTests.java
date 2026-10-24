package com.gesfut.services.impl;

import com.gesfut.dtos.responses.TeamDeletionResponse;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.MatchRepository;
import com.gesfut.repositories.TeamRepository;
import com.gesfut.repositories.TournamentParticipantRepository;
import com.gesfut.services.PlayerService;
import com.gesfut.services.UserEntityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Plan 22/10 #2: Eliminar equipo = borrar si nunca jugo, deshabilitar si tiene historial; bajas en torneo = fecha libre del rival. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamDeleteDisableTests {
    @Mock TeamRepository teams;
    @Mock TournamentParticipantRepository participants;
    @Mock MatchRepository matches;
    @Mock UserEntityService users;
    @Mock PlayerService players;
    @Mock com.gesfut.services.TournamentService tournamentService;
    @InjectMocks TeamServiceImpl service;

    UserEntity owner;
    long ids = 100;

    @BeforeEach void setUp() {
        owner = new UserEntity(); owner.setId(1L);
        when(users.findUserByEmail(any())).thenReturn(owner);
    }

    Team team(String name) {
        Team t = Team.builder().id(ids++).name(name).status(true).user(owner).players(new HashSet<>()).tournaments(new HashSet<>()).build();
        when(teams.findById(t.getId())).thenReturn(Optional.of(t));
        when(participants.findAllByTeamId(t.getId())).thenReturn(new ArrayList<>());
        return t;
    }

    Tournament tournament() {
        Tournament t = new Tournament(); t.setId(ids++); t.setName("TEST torneo"); t.setIsFinished(false);
        t.setMatchDays(new LinkedHashSet<>()); t.setTeams(new HashSet<>());
        return t;
    }

    TournamentParticipant join(Team team, Tournament t) {
        TournamentParticipant p = TournamentParticipant.builder().id(ids++).team(team).tournament(t).isActive(true).playerParticipants(new HashSet<>()).build();
        t.getTeams().add(p);
        List<TournamentParticipant> list = new ArrayList<>(participants.findAllByTeamId(team.getId()));
        list.add(p);
        when(participants.findAllByTeamId(team.getId())).thenReturn(list);
        return p;
    }

    /** Round robin simple: genera las jornadas con el mismo emparejamiento circular que la app. */
    void fixture(Tournament t, List<TournamentParticipant> ps, boolean playoff) {
        List<TournamentParticipant> list = new ArrayList<>(ps);
        int n = list.size();
        for (int d = 0; d < n - 1; d++) {
            MatchDay day = MatchDay.builder().id(ids++).numberOfMatchDay(d).isFinished(false).isPlayOff(false).tournament(t).matches(new HashSet<>()).build();
            for (int j = 0; j < n / 2; j++) {
                day.getMatches().add(Match.builder().id(ids++).homeTeam(list.get(j)).awayTeam(list.get(n - 1 - j)).isFinished(false).goalsHomeTeam(0).goalsAwayTeam(0).matchDay(day).build());
            }
            t.getMatchDays().add(day);
            list.add(1, list.remove(n - 1));
        }
        if (playoff) t.getMatchDays().add(MatchDay.builder().id(ids++).numberOfMatchDay(n).isFinished(false).isPlayOff(true).tournament(t).matches(new HashSet<>()).build());
    }

    /** Juega y cierra la primera jornada. */
    void playFirstDay(Tournament t) {
        MatchDay first = t.getMatchDays().iterator().next();
        first.getMatches().forEach(m -> { if (!m.isByeMatch()) { m.setIsFinished(true); m.setGoalsHomeTeam(1); } else m.setIsFinished(true); });
        first.setIsFinished(true);
    }

    List<Match> matchesOf(Tournament t, TournamentParticipant p) {
        return t.getMatchDays().stream().flatMap(d -> d.getMatches().stream())
                .filter(m -> m.getHomeTeam() == p || m.getAwayTeam() == p).toList();
    }

    @Test void borraEquipoQueNuncaJugo() {
        Team t = team("TEST Nuevo");
        assertEquals("DELETE", service.previewDeleteTeam(t.getId()).action());
        TeamDeletionResponse r = service.deleteTeam(t.getId());
        assertEquals("DELETE", r.action());
        verify(teams).delete(t);
        verify(teams, never()).updateTeamStatus(any(), any());
    }

    @Test void deshabilitaEquipoQueJugoYConservaResultados() {
        Tournament tor = tournament();
        Team a = team("TEST A"), b = team("TEST B"), c = team("TEST C"), d = team("TEST D");
        List<TournamentParticipant> ps = List.of(join(a, tor), join(b, tor), join(c, tor), join(d, tor));
        fixture(tor, ps, false);
        playFirstDay(tor);
        assertEquals("DISABLE", service.previewDeleteTeam(a.getId()).action());
        TeamDeletionResponse r = service.deleteTeam(a.getId());
        assertEquals("DISABLE", r.action());
        verify(teams, never()).delete(any());
        verify(teams).updateTeamStatus(a.getId(), false);
        assertFalse(ps.get(0).getIsActive());
        for (Match m : matchesOf(tor, ps.get(0))) {
            if (Boolean.TRUE.equals(m.getMatchDay().getIsFinished())) {
                assertNotEquals(Boolean.TRUE, m.getBye(), "lo jugado no se toca");
                assertEquals(1, m.getGoalsHomeTeam());
            } else {
                assertTrue(m.isByeMatch());
                String rival = (m.getHomeTeam() == ps.get(0) ? m.getAwayTeam() : m.getHomeTeam()).getTeam().getName();
                assertEquals(rival, m.restingTeamName(), "el rival queda libre");
            }
        }
        // Los partidos entre otros equipos no cambian.
        tor.getMatchDays().stream().flatMap(day -> day.getMatches().stream())
                .filter(m -> m.getHomeTeam() != ps.get(0) && m.getAwayTeam() != ps.get(0))
                .forEach(m -> assertFalse(m.isByeMatch()));
    }

    @Test void torneoImparConFree() {
        Tournament tor = tournament();
        Team free = team("Free");
        Team a = team("TEST A"), b = team("TEST B"), c = team("TEST C");
        List<TournamentParticipant> ps = List.of(join(a, tor), join(b, tor), join(c, tor), join(free, tor));
        fixture(tor, ps, false);
        playFirstDay(tor);
        service.disableTeam(a.getId(), false);
        for (Match m : matchesOf(tor, ps.get(0))) {
            if (Boolean.TRUE.equals(m.getMatchDay().getIsFinished())) continue;
            assertTrue(m.isByeMatch());
        }
        // El partido A vs Free pendiente sigue siendo libre y nadie descansa "de mas".
        tor.getMatchDays().stream().flatMap(d -> d.getMatches().stream())
                .filter(m -> !Boolean.TRUE.equals(m.getMatchDay().getIsFinished()))
                .filter(m -> (m.getHomeTeam() == ps.get(0) || m.getAwayTeam() == ps.get(0)) && (Match.isFreeParticipant(m.getHomeTeam()) || Match.isFreeParticipant(m.getAwayTeam())))
                .forEach(m -> assertNull(m.restingTeamName()));
        // Los partidos contra Free de los demas siguen igual.
        tor.getMatchDays().stream().flatMap(d -> d.getMatches().stream())
                .filter(m -> m.getHomeTeam() != ps.get(0) && m.getAwayTeam() != ps.get(0) && (Match.isFreeParticipant(m.getHomeTeam()) || Match.isFreeParticipant(m.getAwayTeam())))
                .forEach(m -> assertNotNull(m.restingTeamName()));
    }

    @Test void rehabilitar() {
        Tournament tor = tournament(); tor.setIsFinished(true);
        Team a = team("TEST A"); join(a, tor);
        service.deleteTeam(a.getId());
        a.setStatus(false);
        assertNotNull(service.previewDeleteTeam(a.getId()).blockedReason(), "ya deshabilitado");
        service.disableTeam(a.getId(), true);
        verify(teams).updateTeamStatus(a.getId(), true);
        verify(players).updateStatusPlayersByTeam(a.getId(), true);
    }

    @Test void bloqueaEnPlayoffs() {
        Tournament tor = tournament();
        Team a = team("TEST A"), b = team("TEST B");
        List<TournamentParticipant> ps = List.of(join(a, tor), join(b, tor));
        fixture(tor, ps, true);
        TeamDeletionResponse preview = service.previewDeleteTeam(a.getId());
        assertNotNull(preview.blockedReason());
        assertThrows(IllegalArgumentException.class, () -> service.deleteTeam(a.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.disableTeam(a.getId(), false));
        assertTrue(ps.get(0).getIsActive());
        verify(teams, never()).updateTeamStatus(any(), any());
    }

    @Test void torneoTerminadoNoSeToca() {
        Tournament tor = tournament();
        Team a = team("TEST A"), b = team("TEST B");
        List<TournamentParticipant> ps = List.of(join(a, tor), join(b, tor));
        fixture(tor, ps, false);
        tor.setIsFinished(true);
        service.deleteTeam(a.getId());
        assertTrue(ps.get(0).getIsActive());
        assertFalse(tor.getMatchDays().iterator().next().getMatches().iterator().next().isByeMatch());
    }

    @Test void equipoAjenoProhibido() {
        Team t = team("TEST Ajeno");
        UserEntity other = new UserEntity(); other.setId(2L); t.setUser(other);
        assertThrows(AccessDeniedException.class, () -> service.deleteTeam(t.getId()));
        assertThrows(AccessDeniedException.class, () -> service.disableTeam(t.getId(), false));
        verify(teams, never()).delete(any());
    }

    @Test void freeNoSeElimina() {
        Team t = team("Free");
        assertThrows(IllegalArgumentException.class, () -> service.deleteTeam(t.getId()));
    }

    // ---- Plan 22/10 #2 (respuesta 1): torneo sin partidos jugados -> se quita y se regenera el fixture ----
    @Test void torneoSinJugarSeQuitaYRegeneraYSeBorra() {
        Tournament tor = tournament();
        Team a = team("TEST A"), b = team("TEST B"), c = team("TEST C");
        List<TournamentParticipant> ps = List.of(join(a, tor), join(b, tor), join(c, tor));
        fixture(tor, ps, false);
        TeamDeletionResponse preview = service.previewDeleteTeam(a.getId());
        assertEquals("DELETE", preview.action());
        assertEquals(List.of("TEST torneo"), preview.regeneratedTournaments());
        service.deleteTeam(a.getId());
        verify(tournamentService).removeParticipantAndRegenerate(ps.get(0));
        verify(teams).delete(a);
    }

    @Test void torneoSinJugarConOtroHistorialSeDeshabilita() {
        Tournament nuevo = tournament(), viejo = tournament(); viejo.setIsFinished(true);
        Team a = team("TEST A"), b = team("TEST B"), c = team("TEST C");
        TournamentParticipant pa = join(a, nuevo); join(b, nuevo); join(c, nuevo); join(a, viejo);
        TeamDeletionResponse r = service.deleteTeam(a.getId());
        assertEquals("DISABLE", r.action());
        verify(tournamentService).removeParticipantAndRegenerate(pa);
        verify(teams, never()).delete(any());
        verify(teams).updateTeamStatus(a.getId(), false);
    }

    @Test void torneoSinJugarNoBajaDeDosEquipos() {
        Tournament tor = tournament();
        Team a = team("TEST A"), b = team("TEST B"), free = team("Free");
        join(a, tor); join(b, tor); join(free, tor);
        assertNotNull(service.previewDeleteTeam(a.getId()).blockedReason());
        assertThrows(IllegalArgumentException.class, () -> service.deleteTeam(a.getId()));
        verify(tournamentService, never()).removeParticipantAndRegenerate(any());
        verify(teams, never()).delete(any());
    }

    // ---- respuesta 2: al rehabilitar vuelve al torneo en curso ----
    @Test void rehabilitarVuelveAlTorneoYJuegaLoQueFalta() {
        Tournament tor = tournament();
        Team a = team("TEST A"), b = team("TEST B"), c = team("TEST C"), d = team("TEST D");
        List<TournamentParticipant> ps = List.of(join(a, tor), join(b, tor), join(c, tor), join(d, tor));
        fixture(tor, ps, false);
        playFirstDay(tor);
        service.disableTeam(a.getId(), false);
        // la fecha 2 se cierra mientras A esta de baja
        MatchDay second = tor.getMatchDays().stream().filter(x -> x.getNumberOfMatchDay() == 1).findFirst().orElseThrow();
        second.getMatches().forEach(m -> m.setIsFinished(true)); second.setIsFinished(true);
        service.disableTeam(a.getId(), true);
        assertTrue(ps.get(0).getIsActive());
        for (Match m : matchesOf(tor, ps.get(0))) {
            int n = m.getMatchDay().getNumberOfMatchDay();
            if (n == 1) assertTrue(m.isByeMatch(), "fecha pasada sigue libre");
            if (n == 2) assertFalse(m.isByeMatch(), "fecha pendiente se vuelve a jugar");
        }
    }

    @Test void rehabilitarEnPlayoffsNoVuelve() {
        Tournament tor = tournament();
        Team a = team("TEST A"), b = team("TEST B");
        List<TournamentParticipant> ps = List.of(join(a, tor), join(b, tor));
        fixture(tor, ps, true);
        ps.get(0).setIsActive(false);
        service.disableTeam(a.getId(), true);
        assertFalse(ps.get(0).getIsActive());
        verify(teams).updateTeamStatus(a.getId(), true);
    }
}
