package com.gesfut;

import com.gesfut.config.TimeConfig;
import com.gesfut.dtos.requests.*;
import com.gesfut.models.matchDay.*;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.*;
import com.gesfut.repositories.*;
import com.gesfut.services.*;
import com.gesfut.services.impl.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Reglas de horario: un resultado no se carga antes de la hora del partido (hora de Argentina)
 * y la programación acepta fechas/horas pasadas. Unitarios con Mockito y Clock fijo, sin base.
 */
class MatchScheduleRulesTests {
    static final ZoneId ART = TimeConfig.ARGENTINA;
    MatchRepository matches; EventRepository events; StatisticsRepository statistics;
    PlayerParticipantRepository players; EventService eventService; TournamentAccess access;
    Tournament tournament; MatchDay day; TournamentParticipant home, away;

    @BeforeEach void setup(){
        matches=mock(MatchRepository.class); events=mock(EventRepository.class); statistics=mock(StatisticsRepository.class);
        players=mock(PlayerParticipantRepository.class); eventService=mock(EventService.class); access=mock(TournamentAccess.class);
        tournament=Tournament.builder().id(1L).code(UUID.randomUUID()).teams(new HashSet<>()).matchDays(new HashSet<>()).build();
        day=MatchDay.builder().id(1L).numberOfMatchDay(0).isPlayOff(false).isFinished(false).tournament(tournament).matches(new HashSet<>()).build();
        tournament.getMatchDays().add(day);
        home=participant(1L,"TEST Local"); away=participant(2L,"TEST Visitante");
    }
    TournamentParticipant participant(Long id,String name){
        var stats=Statistics.builder().points(0).matchesPlayed(0).wins(0).draws(0).losses(0).goalsFor(0).goalsAgainst(0).redCards(0).yellowCards(0).build();
        var p=TournamentParticipant.builder().id(id).tournament(tournament).team(Team.builder().id(id).name(name).build()).statistics(stats).playerParticipants(new HashSet<>()).build();
        tournament.getTeams().add(p);return p;
    }
    Match match(LocalDateTime artWallTime,boolean finished){
        Match m=Match.builder().id(10L).matchDay(day).homeTeam(home).awayTeam(away).goalsHomeTeam(0).goalsAwayTeam(0)
            .isFinished(finished).events(new ArrayList<>()).date(artWallTime).build();
        day.getMatches().add(m);
        when(matches.findForUpdate(10L)).thenReturn(Optional.of(m));
        return m;
    }
    MatchServiceImpl service(Clock clock){
        var s=new MatchServiceImpl();
        ReflectionTestUtils.setField(s,"matchRepository",matches);ReflectionTestUtils.setField(s,"eventRepository",events);
        ReflectionTestUtils.setField(s,"statisticsRepository",statistics);ReflectionTestUtils.setField(s,"playerParticipantRepository",players);
        ReflectionTestUtils.setField(s,"eventService",eventService);ReflectionTestUtils.setField(s,"access",access);
        ReflectionTestUtils.setField(s,"clock",clock);
        return s;
    }
    static Clock artClock(String artWallTime){
        return Clock.fixed(LocalDateTime.parse(artWallTime).atZone(ART).toInstant(),ART);
    }
    static MatchRequest emptyResult(){return new MatchRequest(10L,List.of(),null);}

    // ---- Defecto 1: no cargar resultados antes de la hora del partido ----

    @Test void loadBeforeKickoffIsRejectedWithArgentineMessage(){
        Match m=match(LocalDateTime.parse("2026-11-14T20:00"),false);
        var ex=assertThrows(IllegalArgumentException.class,()->service(artClock("2026-11-14T19:59")).loadMatchResult(emptyResult()));
        assertEquals("Todavía no se puede cargar el resultado: el partido está programado para el 14/11/2026 a las 20:00 (hora de Argentina).",ex.getMessage());
        assertFalse(m.getIsFinished());
        verify(access).requireOwner(tournament);
        verifyNoInteractions(statistics,eventService);
        verify(matches,never()).save(any());
    }
    @Test void loadExactlyAtKickoffIsAllowed(){
        Match m=match(LocalDateTime.parse("2026-11-14T20:00"),false);
        assertEquals("Partido cargado",service(artClock("2026-11-14T20:00")).loadMatchResult(emptyResult()));
        assertTrue(m.getIsFinished());
        verify(matches).save(m);
    }
    @Test void loadAfterKickoffIsAllowed(){
        Match m=match(LocalDateTime.parse("2026-11-14T20:00"),false);
        assertDoesNotThrow(()->service(artClock("2026-11-15T09:30")).loadMatchResult(emptyResult()));
        assertTrue(m.getIsFinished());
    }
    @Test void kickoffIsReadAsArgentineTimeWhenUtcIsAlreadyNextDay(){
        // 2026-03-15T02:30Z = sáb 14/03 23:30 en Argentina. Partido el dom 15/03 00:30 ART (= 03:30Z).
        // Comparar contra LocalDateTime en UTC (15/03 02:30 > 00:30) lo dejaría pasar por error.
        Instant now=Instant.parse("2026-03-15T02:30:00Z");
        match(LocalDateTime.parse("2026-03-15T00:30"),false);
        var ex=assertThrows(IllegalArgumentException.class,()->service(Clock.fixed(now,ZoneOffset.UTC)).loadMatchResult(emptyResult()));
        assertTrue(ex.getMessage().contains("15/03/2026 a las 00:30 (hora de Argentina)"));
        verify(matches,never()).save(any());
    }
    @Test void kickoffOnArgentineEveningIsAllowedAlthoughUtcDayDiffers(){
        // Partido sáb 14/03 23:00 ART (= 15/03 02:00Z); ahora 15/03 02:30Z: ya empezó.
        Match m=match(LocalDateTime.parse("2026-03-14T23:00"),false);
        assertDoesNotThrow(()->service(Clock.fixed(Instant.parse("2026-03-15T02:30:00Z"),ZoneOffset.UTC)).loadMatchResult(emptyResult()));
        assertTrue(m.getIsFinished());
    }
    @Test void matchWithoutScheduledDateIsNotBlocked(){
        Match m=match(null,false);
        assertDoesNotThrow(()->service(artClock("2026-11-14T10:00")).loadMatchResult(emptyResult()));
        assertTrue(m.getIsFinished());
    }
    @Test void editingResultBeforeKickoffIsRejectedBeforeTouchingEvents(){
        // Partido ya cerrado cuya fecha se reprogramó al futuro: la edición/corrección también respeta el horario.
        match(LocalDateTime.parse("2026-11-21T18:00"),true);
        var ex=assertThrows(IllegalArgumentException.class,()->service(artClock("2026-11-14T12:00")).updateMatchResult(emptyResult()));
        assertTrue(ex.getMessage().startsWith("Todavía no se puede cargar el resultado"));
        verifyNoInteractions(events,statistics);
        verify(matches,never()).save(any());
    }
    @Test void productionClockUsesArgentineZone(){
        assertEquals(ART,new TimeConfig().clock().getZone());
        assertEquals(ZoneId.of("America/Argentina/Buenos_Aires"),ART);
    }

    // ---- Defecto 2: programar con fecha/hora pasada ----

    @Test void schedulingSingleMatchInThePastIsAllowed(){
        Match m=match(LocalDateTime.parse("2026-11-20T20:00"),false);
        LocalDateTime yesterday=LocalDateTime.parse("2026-11-13T21:00");
        var s=service(artClock("2026-11-14T12:00"));
        assertDoesNotThrow(()->s.updateMatchDateAndDescription(10L,new MatchDateRequest(yesterday)));
        assertEquals(yesterday,m.getDate());
        verify(matches).save(m);
        LocalDateTime todayEarlier=LocalDateTime.parse("2026-11-14T08:00");
        assertDoesNotThrow(()->s.updateMatchDateAndDescription(10L,new MatchDateRequest(todayEarlier)));
        assertEquals(todayEarlier,m.getDate());
    }
    @Test void schedulingWholeMatchDayInThePastIsAllowed(){
        match(null,false);
        MatchService matchService=mock(MatchService.class);
        MatchDayRepository days=mock(MatchDayRepository.class);
        when(days.findById(1L)).thenReturn(Optional.of(day));
        var s=new MatchDayServiceImpl();
        ReflectionTestUtils.setField(s,"access",access);ReflectionTestUtils.setField(s,"matchDayRepository",days);
        ReflectionTestUtils.setField(s,"matchService",matchService);
        LocalDateTime past=LocalDateTime.parse("2020-01-01T10:00");
        assertEquals(1,s.updateDateAllMatches(1L,new MatchDateRequest(past),60).size());
        verify(matchService).updateMatchDateAndDescription(10L,new MatchDateRequest(past));
    }
    @Test void requestDtosAcceptPastDates(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var validator=factory.getValidator();
            LocalDateTime past=LocalDateTime.parse("2020-01-01T10:00");
            assertTrue(validator.validate(new MatchDateRequest(past)).isEmpty());
            assertTrue(validator.validate(new MatchDayRequest(UUID.randomUUID().toString(),List.of(1L,2L),past,60,7)).isEmpty());
        }
    }

    // ---- Orden contra la jornada anterior: igual o posterior (>=) al último partido ----
    // Jornada anterior (n° 0) con partidos 20:00 y 22:00 del 07/11: el último es 22:00.
    static final LocalDateTime PREVIOUS_LAST=LocalDateTime.parse("2026-11-07T22:00");
    static final String ORDER_MESSAGE="La fecha no puede ser anterior a la fecha del último partido de la jornada anterior.";

    MatchDay nextDayWithPreviousScheduled(){
        Match early=Match.builder().id(11L).matchDay(day).homeTeam(home).awayTeam(away).goalsHomeTeam(0).goalsAwayTeam(0)
            .isFinished(false).events(new ArrayList<>()).date(PREVIOUS_LAST.minusHours(2)).build();
        Match last=Match.builder().id(12L).matchDay(day).homeTeam(home).awayTeam(away).goalsHomeTeam(0).goalsAwayTeam(0)
            .isFinished(false).events(new ArrayList<>()).date(PREVIOUS_LAST).build();
        day.getMatches().add(early);day.getMatches().add(last);
        MatchDay next=MatchDay.builder().id(2L).numberOfMatchDay(1).isPlayOff(false).isFinished(false).tournament(tournament).matches(new HashSet<>()).build();
        tournament.getMatchDays().add(next);
        Match m=Match.builder().id(20L).matchDay(next).homeTeam(home).awayTeam(away).goalsHomeTeam(0).goalsAwayTeam(0)
            .isFinished(false).events(new ArrayList<>()).date(LocalDateTime.parse("2026-11-14T20:00")).build();
        next.getMatches().add(m);
        when(matches.findForUpdate(20L)).thenReturn(Optional.of(m));
        return next;
    }
    Match onlyMatch(MatchDay d){return d.getMatches().iterator().next();}

    @Test void singleMatchAtSameTimeAsPreviousLastIsAllowed(){
        Match m=onlyMatch(nextDayWithPreviousScheduled());
        assertDoesNotThrow(()->service(artClock("2026-11-01T12:00")).updateMatchDateAndDescription(20L,new MatchDateRequest(PREVIOUS_LAST)));
        assertEquals(PREVIOUS_LAST,m.getDate());
        verify(matches).save(m);
    }
    @Test void singleMatchAfterPreviousLastIsAllowed(){
        Match m=onlyMatch(nextDayWithPreviousScheduled());
        assertDoesNotThrow(()->service(artClock("2026-11-01T12:00")).updateMatchDateAndDescription(20L,new MatchDateRequest(PREVIOUS_LAST.plusMinutes(1))));
        assertEquals(PREVIOUS_LAST.plusMinutes(1),m.getDate());
    }
    @Test void singleMatchBeforePreviousLastIsRejected(){
        Match m=onlyMatch(nextDayWithPreviousScheduled());
        LocalDateTime original=m.getDate();
        var ex=assertThrows(IllegalArgumentException.class,()->service(artClock("2026-11-01T12:00")).updateMatchDateAndDescription(20L,new MatchDateRequest(PREVIOUS_LAST.minusMinutes(1))));
        assertEquals(ORDER_MESSAGE,ex.getMessage());
        assertEquals(original,m.getDate());
        verify(matches,never()).save(any());
    }

    MatchService matchServiceMock; MatchDayServiceImpl dayService(){
        matchServiceMock=mock(MatchService.class);
        MatchDayRepository days=mock(MatchDayRepository.class);
        when(days.findById(2L)).thenReturn(Optional.of(tournament.getMatchDays().stream().filter(d->d.getId()==2L).findFirst().orElseThrow()));
        when(days.findByTournament_IdAndNumberOfMatchDay(1L,0)).thenReturn(Optional.of(day));
        var s=new MatchDayServiceImpl();
        ReflectionTestUtils.setField(s,"access",access);ReflectionTestUtils.setField(s,"matchDayRepository",days);
        ReflectionTestUtils.setField(s,"matchService",matchServiceMock);
        return s;
    }
    @Test void wholeMatchDayAtSameTimeAsPreviousLastIsAllowed(){
        nextDayWithPreviousScheduled();
        assertEquals(1,dayService().updateDateAllMatches(2L,new MatchDateRequest(PREVIOUS_LAST),60).size());
        verify(matchServiceMock).updateMatchDateAndDescription(20L,new MatchDateRequest(PREVIOUS_LAST));
    }
    @Test void wholeMatchDayAfterPreviousLastIsAllowed(){
        nextDayWithPreviousScheduled();
        assertEquals(1,dayService().updateDateAllMatches(2L,new MatchDateRequest(PREVIOUS_LAST.plusMinutes(1)),60).size());
        verify(matchServiceMock).updateMatchDateAndDescription(20L,new MatchDateRequest(PREVIOUS_LAST.plusMinutes(1)));
    }
    @Test void wholeMatchDayBeforePreviousLastIsRejected(){
        nextDayWithPreviousScheduled();
        var s=dayService();
        var ex=assertThrows(IllegalArgumentException.class,()->s.updateDateAllMatches(2L,new MatchDateRequest(PREVIOUS_LAST.minusMinutes(1)),60));
        assertEquals(ORDER_MESSAGE,ex.getMessage());
        verifyNoInteractions(matchServiceMock);
    }
}
