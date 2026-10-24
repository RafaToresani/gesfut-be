package com.gesfut;

import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.TeamService;
import com.gesfut.services.TournamentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Integracion real por HTTP (sin mocks) contra la base de regresion: inicializar con cantidad impar (Free) y par,
 * DNI repetido al sumar equipo / jugador, add-player con DNI (antes 500) y torneo archivado de solo lectura.
 * Crea su propio torneo y equipos y los borra al final.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.open-in-view=false" // como en produccion: sin sesion abierta en el controller
})
@AutoConfigureMockMvc
class InitializeAndDniHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TeamService teams;
    @Autowired TournamentService tournaments;
    @Autowired UserRepository users;
    @Autowired com.gesfut.config.security.jwt.JwtService jwt;
    final String prefix = "TEST INIT " + System.nanoTime() % 100000;
    UserEntity owner; String token; String code;
    final Map<String, Long> ids = new HashMap<>();

    void auth() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities())); }

    @BeforeEach void setUp() throws Exception {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        long uid = jdbc.queryForObject("SELECT user_id FROM teams WHERE LOWER(name)='free' AND user_id IS NOT NULL ORDER BY id LIMIT 1", Long.class);
        owner = users.findById(uid).orElseThrow();
        token = "Bearer " + jwt.getToken(owner, owner.getAuthorities());
        var r = call(post("/api/v1/tournaments").content("{\"name\":\"" + prefix + "\"}"));
        assertTrue(r.getResponse().getStatus() < 300, r.getResponse().getContentAsString());
        code = jdbc.queryForObject("SELECT BIN_TO_UUID(code) FROM tournaments WHERE name=?", String.class, prefix);
        auth();
        String[] dnis = {"40111222", "40333444", "40555666", "40777888", "40999000"};
        for (int i = 0; i < 5; i++) {
            String n = prefix + " " + (char) ('A' + i);
            teams.createTeam(new TeamRequest(n, "#000000", new LinkedHashSet<>(List.of(
                    new PlayerRequest("Capi" + i, "Arquero", true, true, 1, dnis[i]),
                    new PlayerRequest("Otro" + i, "Jugador", false, false, 2)))));
            ids.put(String.valueOf((char) ('A' + i)), jdbc.queryForObject("SELECT id FROM teams WHERE name=?", Long.class, n));
        }
    }

    @AfterEach void clean() {
        auth();
        jdbc.update("UPDATE tournaments SET is_active=1, is_finished=0 WHERE name=?", prefix);
        try { tournaments.deleteTournament(code); } catch (Exception ignored) { }
        jdbc.update("DELETE pp FROM player_participants pp JOIN players p ON p.id=pp.player_id JOIN teams t ON t.id=p.team_id WHERE t.name LIKE ?", prefix + "%");
        jdbc.update("DELETE p FROM players p JOIN teams t ON t.id=p.team_id WHERE t.name LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM teams WHERE name LIKE ?", prefix + "%");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tournaments WHERE name=?", Long.class, prefix));
        SecurityContextHolder.clearContext();
    }

    MvcResult call(MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON)).andReturn();
    }
    String body(MvcResult r) throws Exception { return r.getResponse().getContentAsString(StandardCharsets.UTF_8); }
    MvcResult initialize(String... teamKeys) throws Exception {
        String list = String.join(",", Arrays.stream(teamKeys).map(k -> String.valueOf(ids.get(k))).toList());
        return call(post("/api/v1/tournaments/initialize").content("{\"tournamentCode\":\"" + code + "\",\"teams\":[" + list + "]}"));
    }
    long participants() { return jdbc.queryForObject("SELECT COUNT(*) FROM tournament_participants tp JOIN tournaments t ON t.id=tp.tournament_id WHERE t.name=?", Long.class, prefix); }
    long matchDays() { return jdbc.queryForObject("SELECT COUNT(*) FROM match_days md JOIN tournaments t ON t.id=md.tournament_id WHERE t.name=?", Long.class, prefix); }
    long participantId(String key) { return jdbc.queryForObject("SELECT tp.id FROM tournament_participants tp JOIN tournaments t ON t.id=tp.tournament_id WHERE t.name=? AND tp.team_id=?", Long.class, prefix, ids.get(key)); }

    @Test void imparConFreeInicializa() throws Exception {
        var r = initialize("A", "B", "C");
        assertEquals(200, r.getResponse().getStatus(), body(r));
        assertEquals(4, participants()); // 3 + Free
        assertTrue(matchDays() > 0);
    }

    @Test void parInicializa() throws Exception {
        var r = initialize("A", "B", "C", "D");
        assertEquals(200, r.getResponse().getStatus(), body(r));
        assertEquals(4, participants());
        assertTrue(matchDays() > 0);
    }

    @Test void revisarDetectaDniViejoSinNormalizar() throws Exception {
        jdbc.update("UPDATE players SET dni='40.111.222' WHERE team_id=? AND dni IS NOT NULL", ids.get("B"));
        var r = call(post("/api/v1/tournaments/check-teams").content("{\"tournamentCode\":\"" + code + "\",\"teams\":[" + ids.get("A") + "," + ids.get("B") + "]}"));
        assertEquals(400, r.getResponse().getStatus(), body(r));
        assertTrue(body(r).contains("40111222"), body(r));
    }

    @Test void sumarEquipoConDniRepetidoDa400() throws Exception {
        assertEquals(200, initialize("A", "B", "C").getResponse().getStatus());
        jdbc.update("UPDATE players SET dni='40111222' WHERE team_id=? AND dni IS NOT NULL", ids.get("D"));
        var r = call(post("/api/v1/tournaments/" + code + "/add-team").content("{\"tournamentCode\":\"" + code + "\",\"teams\":[" + ids.get("D") + "]}"));
        assertEquals(400, r.getResponse().getStatus(), body(r));
        assertTrue(body(r).contains("40111222"), body(r));
        assertEquals(4, participants());
    }

    @Test void addPlayerConDniNoDa500YControlaRepetidos() throws Exception {
        assertEquals(200, initialize("A", "B", "C", "D").getResponse().getStatus());
        String url = "/api/v1/team-participant/" + code + "/add-player/" + participantId("A");
        var ok = call(put(url).content("{\"name\":\"Nuevo\",\"lastName\":\"ConDni\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":30,\"dni\":\"41.222.333\"}"));
        assertEquals(200, ok.getResponse().getStatus(), body(ok));
        var dup = call(put(url).content("{\"name\":\"Otro\",\"lastName\":\"Repetido\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":31,\"dni\":\"40333444\"}"));
        assertEquals(400, dup.getResponse().getStatus(), body(dup));
        assertTrue(body(dup).contains("40333444") && body(dup).contains(prefix + " B"), body(dup));
        var noDni = call(put(url).content("{\"name\":\"Sin\",\"lastName\":\"Documento\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":33}"));
        assertEquals(200, noDni.getResponse().getStatus(), body(noDni));
        var team = call(put("/api/v1/teams/add-player/" + ids.get("A")).content("{\"name\":\"Tercero\",\"lastName\":\"Repetido\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":32,\"dni\":\"40-555-666\"}"));
        assertEquals(400, team.getResponse().getStatus(), body(team));
    }

    @Test void archivadoNoPermiteCambiarEstadoDeJugador() throws Exception {
        assertEquals(200, initialize("A", "B", "C", "D").getResponse().getStatus());
        long pp = jdbc.queryForObject("SELECT pp.id FROM player_participants pp WHERE pp.tournament_participant_id=? LIMIT 1", Long.class, participantId("A"));
        jdbc.update("UPDATE tournaments SET is_active=0 WHERE name=?", prefix);
        var r = call(put("/api/v1/team-participant/change-status/" + pp + "/false"));
        assertEquals(409, r.getResponse().getStatus(), body(r));
        jdbc.update("UPDATE tournaments SET is_active=1, is_finished=1 WHERE name=?", prefix);
        assertEquals(409, call(put("/api/v1/team-participant/change-status/" + pp + "/false")).getResponse().getStatus());
    }
}
