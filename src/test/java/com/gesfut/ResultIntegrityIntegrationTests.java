package com.gesfut;

import com.gesfut.dtos.requests.*;
import com.gesfut.models.matchDay.EEventType;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.EventService;
import com.gesfut.services.MatchService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class ResultIntegrityIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MatchService service;
    @Autowired UserRepository users;
    @SpyBean EventService events;
    long matchId, playerId;

    @BeforeEach void fixture(){
        String database=jdbc.queryForObject("SELECT DATABASE()",String.class);
        assertEquals("gesfut_codex_regression_20261013",database,"Estas pruebas solo pueden ejecutarse sobre la copia aislada.");
        var row=jdbc.queryForMap("""
            SELECT m.id, md.id AS day_id, t.user_id, MIN(pp.id) AS player_id
            FROM matches m JOIN match_days md ON md.id=m.match_day_id
            JOIN tournaments t ON t.id=md.tournament_id
            JOIN tournament_participants h ON h.id=m.home_team_id
            JOIN tournament_participants a ON a.id=m.away_team_id
            JOIN teams ht ON ht.id=h.team_id JOIN teams at ON at.id=a.team_id
            JOIN player_participants pp ON pp.tournament_participant_id=h.id
            WHERE md.play_off=0 AND md.is_finished=0 AND m.is_finished=0 AND ht.name<>'Free' AND at.name<>'Free'
            AND NOT EXISTS (SELECT 1 FROM match_days later WHERE later.tournament_id=t.id AND later.play_off=1 AND later.number_of_match_day>md.number_of_match_day)
            GROUP BY m.id, md.id, t.user_id ORDER BY m.id LIMIT 1
            """);
        matchId=((Number)row.get("id")).longValue(); playerId=((Number)row.get("player_id")).longValue();
        UserEntity user=users.findById(((Number)row.get("user_id")).longValue()).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();reset(events);}

    List<Map<String,Object>> fingerprint(){
        var data=new ArrayList<Map<String,Object>>();
        data.addAll(jdbc.queryForList("SELECT id,quantity,type,player_participant_id FROM events WHERE match_id=? ORDER BY id",matchId));
        data.addAll(jdbc.queryForList("SELECT goals_home_team,goals_away_team,is_finished,tiebreak_winner_id FROM matches WHERE id=?",matchId));
        data.addAll(jdbc.queryForList("SELECT s.* FROM statistics s JOIN tournament_participants p ON p.statistics_id=s.id JOIN matches m ON p.id=m.home_team_id OR p.id=m.away_team_id WHERE m.id=? ORDER BY s.id",matchId));
        data.addAll(jdbc.queryForList("SELECT goals,yellow_cards,red_cards,is_mvp,consecutive_cards,is_suspended FROM player_participants WHERE id=?",playerId));
        return data;
    }
    MatchRequest validResult(int goals){return new MatchRequest(matchId,List.of(new EventRequest(playerId,EEventType.GOAL,goals),new EventRequest(playerId,EEventType.YELLOW_CARD,1)),null);}

    @Test void exceptionAfterFirstEventRollsBackAllChanges(){
        var before=fingerprint();var count=new AtomicInteger();
        doAnswer(inv->{ if(count.incrementAndGet()==2)throw new IllegalStateException("Fallo simulado después del primer evento");return inv.callRealMethod(); }).when(events).createEvent(any(),any());
        assertThrows(IllegalStateException.class,()->service.loadMatchResult(validResult(1)));
        assertEquals(2,count.get());assertEquals(before,fingerprint());
    }
    @Test void failedEditPreservesOriginalResultAndStatistics(){
        service.loadMatchResult(validResult(1));var before=fingerprint();
        doThrow(new IllegalStateException("Fallo simulado al editar")).when(events).createEvent(any(),any());
        assertThrows(IllegalStateException.class,()->service.updateMatchResult(validResult(2)));
        assertEquals(before,fingerprint());
    }
    @Test void editingCardsReconstructsDisciplineAndDoesNotDoubleCountGames() throws Exception {
        int before=jdbc.queryForObject("SELECT SUM(s.matches_played) FROM statistics s JOIN tournament_participants p ON p.statistics_id=s.id JOIN matches m ON p.id=m.home_team_id OR p.id=m.away_team_id WHERE m.id=?",Integer.class,matchId);
        service.loadMatchResult(new MatchRequest(matchId,List.of(new EventRequest(playerId,EEventType.RED_CARD,1)),null));
        service.updateMatchResult(new MatchRequest(matchId,List.of(),null));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE match_id=?",Integer.class,matchId));
        assertEquals(before+2,jdbc.queryForObject("SELECT SUM(s.matches_played) FROM statistics s JOIN tournament_participants p ON p.statistics_id=s.id JOIN matches m ON p.id=m.home_team_id OR p.id=m.away_team_id WHERE m.id=?",Integer.class,matchId));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM player_participants WHERE id=? AND is_suspended=1",Integer.class,playerId));
    }
    @Test void anotherManagerCannotWriteMatch(){
        var before=fingerprint();var other=UserEntity.builder().email("other@example.test").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(other,null,List.of()));
        assertThrows(AccessDeniedException.class,()->service.loadMatchResult(validResult(1)));
        assertEquals(before,fingerprint());verify(events,never()).createEvent(any(),any());
    }
    @Test void zeroZeroPlayoffNeedsWinnerAndPreservesZeroGoals(){
        jdbc.update("UPDATE match_days SET play_off=1 WHERE id=(SELECT match_day_id FROM matches WHERE id=?)",matchId);
        assertThrows(IllegalArgumentException.class,()->service.loadMatchResult(new MatchRequest(matchId,List.of(),null)));
        long winner=jdbc.queryForObject("SELECT home_team_id FROM matches WHERE id=?",Long.class,matchId);
        service.loadMatchResult(new MatchRequest(matchId,List.of(),winner));
        var result=service.getDetailedMatchById(matchId);
        assertEquals(0,result.homeGoals());assertEquals(0,result.awayGoals());assertEquals(winner,result.tiebreakWinnerId());assertTrue(result.isFinished());
    }
    @Test void simultaneousSavesCountTheMatchOnlyOnce() throws Exception {
        var principal=SecurityContextHolder.getContext().getAuthentication();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> save=()->{
            SecurityContextHolder.getContext().setAuthentication(principal);
            try {
                start.await();service.loadMatchResult(validResult(1));return true;
            } catch(com.gesfut.exceptions.ResourceNotFoundException | com.gesfut.exceptions.ResourceAlreadyExistsException alreadySaved){return false;} // 22/10: el doble envio ahora es 409
            finally {SecurityContextHolder.clearContext();}
        };
        try {
            var first=pool.submit(save);var second=pool.submit(save);start.countDown();
            boolean one=first.get(15,java.util.concurrent.TimeUnit.SECONDS),two=second.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertNotEquals(one,two);
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE match_id=?",Integer.class,matchId));
            assertEquals(1,jdbc.queryForObject("SELECT goals_home_team FROM matches WHERE id=?",Integer.class,matchId));
        } finally {pool.shutdownNow();}
    }
}
