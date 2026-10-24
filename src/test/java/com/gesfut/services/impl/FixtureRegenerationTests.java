package com.gesfut.services.impl;

import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.repositories.*;
import com.gesfut.services.MatchDayService;
import com.gesfut.services.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Plan 22/10 #2: quitar un equipo de un torneo sin partidos jugados regenera el fixture (par e impar). */
class FixtureRegenerationTests {
    TournamentParticipantRepository participants; MatchDayRepository days; MatchDayService matchDays; TeamService teams;
    TournamentServiceImpl service; Tournament t; long ids = 1;
    Team freeTeam;

    @BeforeEach void setUp() {
        participants = mock(TournamentParticipantRepository.class); days = mock(MatchDayRepository.class);
        matchDays = mock(MatchDayService.class); teams = mock(TeamService.class);
        service = new TournamentServiceImpl();
        ReflectionTestUtils.setField(service, "participantRepository", participants);
        ReflectionTestUtils.setField(service, "matchDayRepository", days);
        ReflectionTestUtils.setField(service, "matchDayService", matchDays);
        ReflectionTestUtils.setField(service, "teamService", teams);
        ReflectionTestUtils.setField(service, "access", mock(TournamentAccess.class));
        ReflectionTestUtils.setField(service, "playerParticipantRepository", mock(PlayerParticipantRepository.class));
        t = Tournament.builder().id(99L).name("TEST Regen").code(UUID.randomUUID()).isFinished(false).teams(new HashSet<>()).matchDays(new HashSet<>()).build();
        freeTeam = Team.builder().id(500L).name("Free").status(true).players(new HashSet<>()).build();
        when(teams.getTeamByName()).thenReturn(freeTeam);
        when(teams.getTeamByIdSecured(500L)).thenReturn(freeTeam);
        when(participants.save(any())).thenAnswer(inv -> { TournamentParticipant p = inv.getArgument(0); p.setId(ids++ + 1000); t.getTeams().add(p); return p; });
        when(participants.findAllByTournament(t)).thenAnswer(inv -> new HashSet<>(t.getTeams()));
    }

    TournamentParticipant p(String name) {
        Team team = Team.builder().id(ids++).name(name).status(true).players(new HashSet<>()).tournaments(new HashSet<>()).build();
        TournamentParticipant p = TournamentParticipant.builder().id(ids++).team(team).tournament(t).isActive(true).build();
        t.getTeams().add(p); return p;
    }

    void day(TournamentParticipant h, TournamentParticipant a, boolean finished, LocalDateTime date) {
        MatchDay d = MatchDay.builder().id(ids++).numberOfMatchDay(t.getMatchDays().size()).isFinished(false).isPlayOff(false).tournament(t).matches(new HashSet<>()).build();
        d.getMatches().add(Match.builder().id(ids++).homeTeam(h).awayTeam(a).isFinished(finished).date(date).matchDay(d).build());
        t.getMatchDays().add(d);
    }

    @SuppressWarnings("unchecked")
    Set<TournamentParticipant> regenerated() {
        ArgumentCaptor<HashSet<TournamentParticipant>> c = ArgumentCaptor.forClass(HashSet.class);
        verify(matchDays).generateMatchDays(c.capture(), eq(t.getCode().toString()), any(), any(), any());
        return c.getValue();
    }

    @Test void parQuedaImparYAgregaFree() {
        var a = p("TEST A"); var b = p("TEST B"); p("TEST C"); p("TEST D");
        day(a, b, false, null);
        service.removeParticipantAndRegenerate(a);
        Set<TournamentParticipant> r = regenerated();
        assertEquals(4, r.size());
        assertTrue(r.stream().anyMatch(Match::isFreeParticipant));
        assertFalse(r.contains(a));
        assertTrue(t.getMatchDays().isEmpty(), "se borran las fechas viejas");
        verify(participants).delete(a);
    }

    @Test void imparQuedaParYSacaFree() {
        var a = p("TEST A"); p("TEST B"); p("TEST C"); var free = p("Free"); free.getTeam().setName("Free");
        service.removeParticipantAndRegenerate(a);
        Set<TournamentParticipant> r = regenerated();
        assertEquals(2, r.size());
        assertTrue(r.stream().noneMatch(Match::isFreeParticipant));
        verify(participants).delete(free);
    }

    @Test void conservaLaProgramacion() {
        var a = p("TEST A"); var b = p("TEST B"); var c = p("TEST C"); var d = p("TEST D");
        LocalDateTime start = LocalDateTime.of(2030, 1, 5, 18, 0);
        MatchDay d0 = MatchDay.builder().id(ids++).numberOfMatchDay(0).isFinished(false).isPlayOff(false).tournament(t).matches(new HashSet<>()).build();
        d0.getMatches().add(Match.builder().id(ids++).homeTeam(a).awayTeam(b).isFinished(false).date(start).matchDay(d0).build());
        d0.getMatches().add(Match.builder().id(ids++).homeTeam(c).awayTeam(d).isFinished(false).date(start.plusMinutes(45)).matchDay(d0).build());
        t.getMatchDays().add(d0);
        MatchDay d1 = MatchDay.builder().id(ids++).numberOfMatchDay(1).isFinished(false).isPlayOff(false).tournament(t).matches(new HashSet<>()).build();
        d1.getMatches().add(Match.builder().id(ids++).homeTeam(a).awayTeam(c).isFinished(false).date(start.plusDays(3)).matchDay(d1).build());
        t.getMatchDays().add(d1);
        service.removeParticipantAndRegenerate(d);
        verify(matchDays).generateMatchDays(any(), any(), eq(start), eq(45), eq(3));
    }

    @Test void torneoArrancadoNoSeRegenera() {
        var a = p("TEST A"); var b = p("TEST B"); p("TEST C");
        day(a, b, true, null);
        assertThrows(IllegalArgumentException.class, () -> service.removeParticipantAndRegenerate(a));
        verify(matchDays, never()).generateMatchDays(any(), any(), any(), any(), any());
    }

    @Test void noBajaDeDos() {
        var a = p("TEST A"); p("TEST B");
        assertThrows(IllegalArgumentException.class, () -> service.removeParticipantAndRegenerate(a));
        verify(participants, never()).delete(any());
    }
}
