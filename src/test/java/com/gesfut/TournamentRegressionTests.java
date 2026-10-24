package com.gesfut;

import com.gesfut.models.matchDay.*;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.*;
import com.gesfut.models.user.UserEntity;
import com.gesfut.dtos.requests.*;
import com.gesfut.repositories.*;
import com.gesfut.services.impl.*;
import com.gesfut.services.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.validation.Validation;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TournamentRegressionTests {
    TournamentRepository tournaments;
    MatchDayRepository days;
    MatchRepository matches;
    TournamentAccess access;
    TournamentServiceImpl service;
    Tournament tournament;
    TournamentParticipant a,b,c,d;

    @BeforeEach void setup() {
        tournaments=mock(TournamentRepository.class); days=mock(MatchDayRepository.class);
        matches=mock(MatchRepository.class); access=mock(TournamentAccess.class);
        service=new TournamentServiceImpl();
        ReflectionTestUtils.setField(service,"tournamentRepository",tournaments);
        ReflectionTestUtils.setField(service,"matchDayRepository",days);
        ReflectionTestUtils.setField(service,"access",access);
        tournament=Tournament.builder().id(1L).code(UUID.randomUUID()).teams(new HashSet<>()).matchDays(new HashSet<>()).build();
        a=participant(1L,"Zulu"); b=participant(2L,"Alfa"); c=participant(3L,"Beta"); d=participant(4L,"Gamma");
        when(tournaments.findByCode(tournament.getCode())).thenReturn(Optional.of(tournament));
        when(days.save(any())).thenAnswer(inv -> {MatchDay day=inv.getArgument(0);long id=10;for(Match m:day.getMatches())m.setId(id++);return day;});
    }
    @AfterEach void clearContext(){SecurityContextHolder.clearContext();}
    TournamentParticipant participant(Long id,String name){
        var p=TournamentParticipant.builder().id(id).tournament(tournament).team(Team.builder().id(id).name(name).build()).playerParticipants(new HashSet<>()).build();
        tournament.getTeams().add(p);return p;
    }
    MatchDay day(boolean playoff,boolean finished){
        MatchDay day=MatchDay.builder().id(1L).numberOfMatchDay(0).isPlayOff(playoff).isFinished(finished).tournament(tournament).matches(new HashSet<>()).build();
        tournament.getMatchDays().add(day);return day;
    }
    Match match(MatchDay day,TournamentParticipant home,TournamentParticipant away,int hg,int ag,boolean finished){
        Match m=Match.builder().id((long)day.getMatches().size()+1).matchDay(day).homeTeam(home).awayTeam(away).goalsHomeTeam(hg).goalsAwayTeam(ag).isFinished(finished).events(new ArrayList<>()).build();
        day.getMatches().add(m);return m;
    }

    @Test void playoffsUsePointsInsteadOfAlphabeticalOrder(){
        MatchDay league=day(false,true);
        match(league,a,b,3,0,true); match(league,c,d,1,0,true);
        service.generatePlayOffs(tournament.getCode().toString(),List.of(1L,2L,3L,4L));
        MatchDay playoffs=tournament.getMatchDays().stream().filter(MatchDay::getIsPlayOff).findFirst().orElseThrow();
        Match top=playoffs.getMatches().stream().filter(m->m.getHomeTeam()==a).findFirst().orElseThrow();
        assertSame(b,top.getAwayTeam()); // Zulu primero por puntos/DG, Alfa último.
        verify(access).requireOwner(tournament);
        assertNotEquals(Boolean.TRUE, tournament.getIsFinished()); // generar playoffs no toca "finalizado"
    }
    /** Plan 22/10 #2 (respuesta 3): un equipo dado de baja no clasifica a playoffs. */
    @Test void inactiveParticipantCannotQualify(){
        MatchDay league=day(false,true);
        match(league,a,b,3,0,true); match(league,c,d,1,0,true);
        a.setIsActive(false);
        var e=assertThrows(IllegalArgumentException.class,()->service.generatePlayOffs(tournament.getCode().toString(),List.of(1L,2L,3L,4L)));
        assertTrue(e.getMessage().contains("dados de baja"));
        assertTrue(tournament.getMatchDays().stream().noneMatch(MatchDay::getIsPlayOff));
    }
    /** Revision critica #1: siembra clasica 1v8, 4v5 | 2v7, 3v6; 1 y 2 solo se cruzan en la final. */
    @Test void eightTeamBracketUsesClassicSeeding(){
        var e=participant(5L,"E"); var f=participant(6L,"F"); var g=participant(7L,"G"); var h=participant(8L,"H");
        MatchDay league=day(false,true);
        // a..d ganan con diferencia 5,4,3,2 -> ranking a,b,c,d,h,g,f,e
        match(league,a,e,5,0,true); match(league,b,f,4,0,true); match(league,c,g,3,0,true); match(league,d,h,2,0,true);
        service.generatePlayOffs(tournament.getCode().toString(),List.of(1L,2L,3L,4L,5L,6L,7L,8L));
        MatchDay po=tournament.getMatchDays().stream().filter(MatchDay::getIsPlayOff).findFirst().orElseThrow();
        List<Match> ms=po.getMatches().stream().sorted(Comparator.comparingLong(Match::getId)).toList();
        assertEquals(List.of("Zulu-E","Gamma-H","Alfa-F","Beta-G"), ms.stream().map(m->m.getHomeTeam().getTeam().getName()+"-"+m.getAwayTeam().getTeam().getName()).toList());
        assertEquals(ms.get(1).getId(), ms.get(0).getVsMatchIdWhoWin()); // 1/8 contra 4/5
        assertEquals(ms.get(3).getId(), ms.get(2).getVsMatchIdWhoWin()); // 2/7 contra 3/6
        assertEquals(List.of(1,4,2,3), com.gesfut.services.impl.TournamentSeedAccess.order(4));
        assertEquals(List.of(1,2), com.gesfut.services.impl.TournamentSeedAccess.order(2));
    }
    /** Revision critica #2: finalizar es explicito, exige todo jugado y es irreversible. */
    @Test void finishRequiresEverythingPlayedAndIsIrreversible(){
        MatchDay league=day(false,false); match(league,a,b,1,0,true);
        assertThrows(IllegalArgumentException.class,()->service.finishTournament(tournament.getCode().toString()));
        league.setIsFinished(true);
        service.finishTournament(tournament.getCode().toString());
        assertEquals(Boolean.TRUE,tournament.getIsFinished());
        doCallRealMethod().when(access).requireOpen(any());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.finishTournament(tournament.getCode().toString()));
    }
    @Test void finishWaitsForPlayoffFinal(){
        MatchDay league=day(false,true); match(league,a,b,1,0,true); match(league,c,d,1,0,true);
        MatchDay semis=MatchDay.builder().id(2L).numberOfMatchDay(1).isPlayOff(true).isFinished(true).tournament(tournament).matches(new HashSet<>()).build();
        tournament.getMatchDays().add(semis); match(semis,a,d,1,0,true); match(semis,b,c,1,0,true);
        var e=assertThrows(IllegalArgumentException.class,()->service.finishTournament(tournament.getCode().toString()));
        assertTrue(e.getMessage().contains("playoffs"));
    }
    @Test void sixQualifiersAreRejectedBeforeCreatingBrokenBracket(){
        day(false,true); participant(5L,"E");participant(6L,"F");
        assertThrows(IllegalArgumentException.class,()->service.generatePlayOffs(tournament.getCode().toString(),List.of(1L,2L,3L,4L,5L,6L)));
        verify(days,never()).save(any());
    }
    @Test void finalCannotGenerateAnotherRound(){
        match(day(true,true),a,b,1,0,true);
        assertThrows(IllegalArgumentException.class,()->service.generatePlayOffs(tournament.getCode().toString(),List.of()));
        verify(days,never()).save(any());
    }
    @Test void tieWithoutWinnerCannotAdvance(){
        MatchDay semi=day(true,true); Match one=match(semi,a,b,0,0,true); Match two=match(semi,c,d,1,0,true);
        one.setVsMatchIdWhoWin(two.getId());two.setVsMatchIdWhoWin(one.getId());
        assertThrows(IllegalArgumentException.class,()->service.generatePlayOffs(tournament.getCode().toString(),List.of()));
        verify(days,never()).save(any());
    }
    @Test void explicitTieWinnerAdvancesWithoutChangingScore(){
        MatchDay semi=day(true,true); Match one=match(semi,a,b,0,0,true); Match two=match(semi,c,d,1,0,true);
        one.setTiebreakWinnerId(b.getId());one.setVsMatchIdWhoWin(two.getId());two.setVsMatchIdWhoWin(one.getId());
        service.generatePlayOffs(tournament.getCode().toString(),List.of());
        MatchDay finalDay=tournament.getMatchDays().stream().max(Comparator.comparingInt(MatchDay::getNumberOfMatchDay)).orElseThrow();
        assertEquals(1,finalDay.getMatches().size());assertSame(b,finalDay.getMatches().iterator().next().getHomeTeam());
        assertEquals(0,one.getGoalsHomeTeam()); assertEquals(0,one.getGoalsAwayTeam());
    }
    @Test void ownershipRejectsDifferentAndAnonymousUsers(){
        tournament.setUser(UserEntity.builder().email("owner@example.test").build());
        TournamentAccess real=new TournamentAccess();
        assertThrows(AccessDeniedException.class,()->real.requireOwner(tournament));
        var other=UserEntity.builder().email("other@example.test").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(other,null,List.of()));
        assertThrows(AccessDeniedException.class,()->real.requireOwner(tournament));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(tournament.getUser(),null,List.of()));
        assertDoesNotThrow(()->real.requireOwner(tournament));
    }
    MatchDayServiceImpl dayService(){
        var s=new MatchDayServiceImpl();ReflectionTestUtils.setField(s,"access",access);
        ReflectionTestUtils.setField(s,"tournamentRepository",tournaments);ReflectionTestUtils.setField(s,"matchDayRepository",days);return s;
    }
    @Test void regenerationPreservesPlayedMatchesInOpenDayByRejectingChange(){
        match(day(false,false),a,b,2,1,true);
        assertThrows(IllegalArgumentException.class,()->dayService().reGenerateMatchDays(List.of(a,b,c,d),tournament.getCode().toString()));
        verify(days,never()).deleteAll(any());
        assertEquals(1,tournament.getMatchDays().size());
    }
    @Test void regenerationExcludesPlayedPairsAndInitializesScores(){
        MatchDay closed=day(false,true);match(closed,a,b,1,0,true);match(closed,c,d,1,0,true);
        dayService().reGenerateMatchDays(List.of(a,b,c,d),tournament.getCode().toString());
        Set<String> pairs=new HashSet<>();
        for(MatchDay day:tournament.getMatchDays())for(Match m:day.getMatches()){
            assertNotNull(m.getGoalsHomeTeam());assertNotNull(m.getGoalsAwayTeam());assertNotNull(day.getIsPlayOff());
            assertTrue(pairs.add(Math.min(m.getHomeTeam().getId(),m.getAwayTeam().getId())+":"+Math.max(m.getHomeTeam().getId(),m.getAwayTeam().getId())));
        }
        assertEquals(6,pairs.size());assertTrue(tournament.getMatchDays().contains(closed));
    }
    @Test void missingMatchDateReturnsDomainErrorInsteadOfOptionalFailure(){
        var s=new MatchServiceImpl();ReflectionTestUtils.setField(s,"matchRepository",matches);ReflectionTestUtils.setField(s,"access",access);
        when(matches.findForUpdate(999L)).thenReturn(Optional.empty());
        assertThrows(com.gesfut.exceptions.ResourceNotFoundException.class,()->s.updateMatchDateAndDescription(999L,new MatchDateRequest(java.time.LocalDateTime.now())));
    }
    @Test void eventsAreValidatedRecursivelyIncludingNullAndNegativeInputs(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var validator=factory.getValidator();
            assertFalse(validator.validate(new MatchRequest(1L,List.of(new EventRequest(null,null,-1)),null)).isEmpty());
            assertFalse(validator.validate(new MatchRequest(1L,null,null)).isEmpty());
            assertTrue(validator.validate(new MatchRequest(1L,List.of(),null)).isEmpty());
        }
    }
    @Test void unresolvedFinalNeverReportsVisitorAsChampion(){
        prepareResponseServices();
        Match finalMatch=match(day(true,true),a,b,0,0,true);
        assertNull(service.tournamentToResponse(tournament).championOfPlayoffsId());
        finalMatch.setTiebreakWinnerId(a.getId());
        assertEquals(a.getId(),service.tournamentToResponse(tournament).championOfPlayoffsId());
    }
    @Test void playoffPointsNeverContaminateLeagueClassification(){
        prepareResponseServices();
        MatchDay league=day(false,true);match(league,a,b,2,0,true);
        MatchDay playoff=day(true,true);playoff.setNumberOfMatchDay(1);match(playoff,b,a,5,0,true);
        var response=service.tournamentToResponse(tournament);
        assertEquals(a.getId(),response.championOfLeagueId());assertEquals(b.getId(),response.championOfPlayoffsId());
        var top=response.participants().getFirst();
        assertEquals(a.getId(),top.idParticipant());assertEquals(3,top.statistics().points());assertEquals(2,top.statistics().goalsFor());
    }
    /** Revision critica #6: la ronda de playoffs se genera con fecha y hora escalonada. */
    @Test void playoffRoundGetsDateAndTime(){
        MatchDay league=day(false,true);
        Match last=match(league,a,b,3,0,true); match(league,c,d,1,0,true);
        last.setDate(java.time.LocalDateTime.now().minusDays(1));
        var start=java.time.LocalDate.now().plusDays(3).atTime(20,0);
        assertThrows(IllegalArgumentException.class,()->service.generatePlayOffs(tournament.getCode().toString(),List.of(1L,2L,3L,4L),java.time.LocalDateTime.now().minusDays(2),60));
        service.generatePlayOffs(tournament.getCode().toString(),List.of(1L,2L,3L,4L),start,60);
        MatchDay po=tournament.getMatchDays().stream().filter(MatchDay::getIsPlayOff).findFirst().orElseThrow();
        var dates=po.getMatches().stream().sorted(Comparator.comparingLong(Match::getId)).map(Match::getDate).toList();
        assertEquals(List.of(start,start.plusMinutes(60)),dates);
        assertFalse(po.getMatches().iterator().next().resultLoadableToday()); // regla del dia del partido
    }
    /** Revision critica #8: empate en puntos/DG/GF -> enfrentamiento directo -> fair play -> nombre. */
    @Test void tiebreakUsesHeadToHeadThenFairPlayThenName(){
        MatchDay league=day(false,true);
        match(league,a,b,1,0,true); match(league,d,a,1,0,true); match(league,b,c,1,0,true);
        // a (Zulu) y b (Alfa): 3 pts, DG 0, GF 1; a le gano a b -> a primero aunque el nombre ordene al reves
        List<TournamentParticipant> ranked=new ArrayList<>(List.of(b,a));
        ReflectionTestUtils.invokeMethod(service,"sortParticipants",ranked);
        assertSame(a,ranked.get(0)); assertSame(b,ranked.get(1));
    }
    @Test void circularTieFallsBackToFewerCardsThenName(){
        MatchDay league=day(false,true);
        match(league,a,b,1,0,true); Match m=match(league,c,a,1,0,true); match(league,b,c,1,0,true);
        var pp=com.gesfut.models.tournament.PlayerParticipant.builder().id(90L).tournamentParticipant(c).build();
        m.getEvents().add(com.gesfut.models.matchDay.Event.builder().id(91L).type(com.gesfut.models.matchDay.EEventType.YELLOW_CARD).quantity(1).playerParticipant(pp).match(m).build());
        List<TournamentParticipant> ranked=new ArrayList<>(List.of(c,a,b));
        ReflectionTestUtils.invokeMethod(service,"sortParticipants",ranked);
        assertEquals(List.of("Alfa","Zulu","Beta"),ranked.stream().map(p->p.getTeam().getName()).toList());
    }
    /** Revision critica #10: goleadores y tarjetas cuentan solo partidos de liga. */
    @Test void scorersAndCardsCountLeagueOnly(){
        when(tournaments.findDetailedByCode(tournament.getCode())).thenReturn(Optional.of(tournament));
        var player=com.gesfut.models.tournament.PlayerParticipant.builder().id(70L).tournamentParticipant(a)
            .player(com.gesfut.models.team.Player.builder().name("Juan").lastName("TEST").build()).goals(5).yellowCards(2).redCards(0).build();
        MatchDay league=day(false,true); Match lm=match(league,a,b,2,0,true);
        MatchDay po=day(true,true); po.setNumberOfMatchDay(1); Match pm=match(po,a,b,3,0,true);
        lm.getEvents().add(com.gesfut.models.matchDay.Event.builder().id(1L).type(com.gesfut.models.matchDay.EEventType.GOAL).quantity(2).playerParticipant(player).match(lm).build());
        pm.getEvents().add(com.gesfut.models.matchDay.Event.builder().id(2L).type(com.gesfut.models.matchDay.EEventType.GOAL).quantity(3).playerParticipant(player).match(pm).build());
        pm.getEvents().add(com.gesfut.models.matchDay.Event.builder().id(3L).type(com.gesfut.models.matchDay.EEventType.YELLOW_CARD).quantity(1).playerParticipant(player).match(pm).build());
        var scorers=service.findTopScorersByTournament(tournament.getCode().toString());
        assertEquals(1,scorers.size()); assertEquals(2,scorers.getFirst().goals());
        assertTrue(service.findTopYellowCardsByTournament(tournament.getCode().toString()).isEmpty());
    }
    /** Revision critica #15: borrar = definitivo si no se jugo nada; si hubo partidos, archivar. */
    @Test void deleteWithoutPlayedMatchesIsHardDelete(){
        tournament.setUser(UserEntity.builder().tournaments(new HashSet<>(Set.of(tournament))).build());
        match(day(false,false),a,b,0,0,false);
        assertEquals("DELETE",service.deleteTournament(tournament.getCode().toString()));
        verify(access).requireOwner(tournament);
        verify(tournaments).delete(tournament);
        assertTrue(tournament.getUser().getTournaments().isEmpty());
    }
    @Test void deleteWithPlayedMatchesArchives(){
        tournament.setIsActive(true);
        match(day(false,true),a,b,1,0,true);
        assertEquals("ARCHIVE",service.deleteTournament(tournament.getCode().toString()));
        assertEquals(Boolean.FALSE,tournament.getIsActive());
        verify(tournaments,never()).delete(any());
    }
    /** Respuesta 2: una roja (3) pesa mas que dos amarillas (2). */
    @Test void fairPlayRedWeighsThree(){
        MatchDay league=day(false,true);
        Match m1=match(league,a,b,1,0,true); Match m2=match(league,c,a,1,0,true); match(league,b,c,1,0,true);
        var pa=com.gesfut.models.tournament.PlayerParticipant.builder().id(80L).tournamentParticipant(a).build();
        var pb=com.gesfut.models.tournament.PlayerParticipant.builder().id(81L).tournamentParticipant(b).build();
        m1.getEvents().add(com.gesfut.models.matchDay.Event.builder().id(82L).type(com.gesfut.models.matchDay.EEventType.RED_CARD).quantity(1).playerParticipant(pa).match(m1).build());
        m2.getEvents().add(com.gesfut.models.matchDay.Event.builder().id(83L).type(com.gesfut.models.matchDay.EEventType.YELLOW_CARD).quantity(2).playerParticipant(pb).match(m2).build());
        List<TournamentParticipant> ranked=new ArrayList<>(List.of(a,b,c));
        ReflectionTestUtils.invokeMethod(service,"sortParticipants",ranked);
        assertEquals(List.of("Beta","Alfa","Zulu"),ranked.stream().map(p->p.getTeam().getName()).toList());
    }
    void prepareResponseServices(){
        tournament.setUser(UserEntity.builder().name("Test").lastname("Owner").build());
        var participants=mock(TournamentParticipantService.class);
        when(participants.participantToResponse(any())).thenAnswer(inv->{TournamentParticipant p=inv.getArgument(0);return new com.gesfut.dtos.responses.ParticipantResponse(p.getId(),p.getTeam().getId(),p.getTeam().getName(),true,null,Set.of());});
        ReflectionTestUtils.setField(service,"participantService",participants);
        var matchDays=mock(MatchDayService.class);
        when(matchDays.matchDayToResponse(any())).thenAnswer(inv->{MatchDay d=inv.getArgument(0);return new com.gesfut.dtos.responses.MatchDayResponse(d.getId(),d.getNumberOfMatchDay(),d.getIsFinished(),null,List.of(),d.getIsPlayOff());});
        ReflectionTestUtils.setField(service,"matchDayService",matchDays);
    }
}

