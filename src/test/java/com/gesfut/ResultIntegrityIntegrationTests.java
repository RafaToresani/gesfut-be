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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate"
})
// Datos propios y solo TEST: antes de cada test se limpia (por si una corrida anterior se corto) y se siembra;
// despues de cada test (aunque falle) se limpia. Cada DELETE va limitado por los ids sembrados y el prefijo TEST.
// No se usa @Transactional con rollback: el test usa hilos (simultaneousSaves...) y verifica los rollbacks
// del propio servicio, asi que necesita datos commiteados.
// result-integrity-db-guard.sql va PRIMERO en ambos @Sql: si DATABASE() no es gesfut_isolated_test, falla
// antes de cualquier cleanup o seed (los scripts de un @Sql corren en orden, en una misma conexion,
// y con FAIL_ON_ERROR un error corta los siguientes).
@SqlConfig(encoding = "UTF-8", errorMode = SqlConfig.ErrorMode.FAIL_ON_ERROR)
@Sql(scripts = {"/sql/result-integrity-db-guard.sql", "/sql/result-integrity-cleanup.sql", "/sql/result-integrity-seed.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = {"/sql/result-integrity-db-guard.sql", "/sql/result-integrity-cleanup.sql"},
     executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ResultIntegrityIntegrationTests {
    // Ids fijos sembrados por src/test/resources/sql/result-integrity-seed.sql (rango reservado 990001/990002).
    static final long TEST_USER_ID = 990001L, TEST_MATCH_ID = 990001L, TEST_PLAYER_PARTICIPANT_ID = 990001L;
    @Autowired JdbcTemplate jdbc;
    @Autowired MatchService service;
    @Autowired UserRepository users;
    @SpyBean EventService events;
    long matchId, playerId;

    @BeforeEach void fixture(){
        String database=jdbc.queryForObject("SELECT DATABASE()",String.class);
        assertEquals("gesfut_isolated_test",database,"Estas pruebas solo pueden ejecutarse sobre la base aislada.");
        // Solo ids sembrados por este test: no se lee ningun dato preexistente.
        matchId=TEST_MATCH_ID; playerId=TEST_PLAYER_PARTICIPANT_ID;
        UserEntity user=users.findById(TEST_USER_ID).orElseThrow();
        assertTrue(user.getEmail().startsWith("test+")&&user.getEmail().endsWith("@example.invalid"),"El organizador debe ser el usuario TEST sembrado.");
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
        var before=fingerprint();var other=UserEntity.builder().email("test+otro-organizador@example.invalid").build();
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
    @Test void correctionOnClosedDayRecalculatesAndIsAudited() throws Exception {
        service.loadMatchResult(validResult(1));
        long dayId=jdbc.queryForObject("SELECT match_day_id FROM matches WHERE id=?",Long.class,matchId);
        jdbc.update("UPDATE match_days SET is_finished=1 WHERE id=?",dayId);
        try {
            int audits=jdbc.queryForObject("SELECT COUNT(*) FROM result_corrections WHERE match_id=?",Integer.class,matchId);
            service.updateMatchResult(validResult(3));
            assertEquals(3,jdbc.queryForObject("SELECT goals_home_team FROM matches WHERE id=?",Integer.class,matchId));
            assertEquals(audits+1,jdbc.queryForObject("SELECT COUNT(*) FROM result_corrections WHERE match_id=?",Integer.class,matchId));
            var audit=jdbc.queryForMap("SELECT old_result,new_result,user_email FROM result_corrections WHERE match_id=? ORDER BY id DESC LIMIT 1",matchId);
            assertTrue(audit.get("old_result").toString().contains(" 1-0 "),audit.toString());
            assertTrue(audit.get("new_result").toString().contains(" 3-0 "),audit.toString());
            assertNotNull(audit.get("user_email"));
        } finally { jdbc.update("UPDATE match_days SET is_finished=0 WHERE id=?",dayId); }
    }
    /** Revision critica #9: se guarda la tanda de penales y define el ganador. */
    @Test void penaltyShootoutIsStoredAndDecidesWinner(){
        jdbc.update("UPDATE match_days SET play_off=1 WHERE id=(SELECT match_day_id FROM matches WHERE id=?)",matchId);
        long home=jdbc.queryForObject("SELECT home_team_id FROM matches WHERE id=?",Long.class,matchId);
        long away=jdbc.queryForObject("SELECT away_team_id FROM matches WHERE id=?",Long.class,matchId);
        assertThrows(IllegalArgumentException.class,()->service.loadMatchResult(new MatchRequest(matchId,List.of(),null,3,3)));
        assertThrows(IllegalArgumentException.class,()->service.loadMatchResult(new MatchRequest(matchId,List.of(),home,2,4)));
        service.loadMatchResult(new MatchRequest(matchId,List.of(),null,2,4));
        var result=service.getDetailedMatchById(matchId);
        assertEquals(away,result.tiebreakWinnerId());assertEquals(2,result.penaltiesHome());assertEquals(4,result.penaltiesAway());
    }
    @Test void penaltiesOnlyForTiedPlayoff(){
        assertThrows(IllegalArgumentException.class,()->service.loadMatchResult(new MatchRequest(matchId,List.of(),null,4,3)));
    }
}
