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
        assertFalse(tournament.getIsFinished());
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

