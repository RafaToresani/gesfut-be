package com.gesfut;

import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.TeamService;
import com.gesfut.services.TournamentService;
import com.gesfut.services.impl.AuthServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** PUT /team-participant/{code}/add-player/{id}: @Valid (400) y regla de dorsal activo, por HTTP. Crea sus propios datos TEST y los borra al final. */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
class AddPlayerHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthServiceImpl authService;
    @Autowired TeamService teams;
    @Autowired TournamentService tournaments;
    @Autowired com.gesfut.config.security.jwt.JwtService jwt;
    final String prefix = "TEST ADD " + java.util.UUID.randomUUID().toString().substring(0, 8);
    final String email = "qa.add." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    Map<String, Object> row;
    UserEntity owner; String code;

    @BeforeEach void setUp() {
        assertEquals("gesfut_isolated_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        authService.singUp(new RegisterRequest(email, "Qa-" + java.util.UUID.randomUUID(), "QaAdd", "User", true));
        owner = users.findByEmail(email).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
        teams.createTeam(new TeamRequest(prefix + " A", "#000000", new LinkedHashSet<>(List.of(
                new PlayerRequest("Ana", "Perez", true, false, 10), new PlayerRequest("Beto", "Gomez", false, true, 1)))));
        teams.createTeam(new TeamRequest(prefix + " B", "#000000", new LinkedHashSet<>(List.of(
                new PlayerRequest("Cata", "Ruiz", true, false, 7), new PlayerRequest("Dani", "Sosa", false, true, 2)))));
        code = tournaments.createTournament(new TournamentRequest(prefix));
        long teamA = jdbc.queryForObject("SELECT id FROM teams WHERE name=?", Long.class, prefix + " A");
        long teamB = jdbc.queryForObject("SELECT id FROM teams WHERE name=?", Long.class, prefix + " B");
        tournaments.initializeTournament(new MatchDayRequest(code, new ArrayList<>(List.of(teamA, teamB)), null, null, null), null);
        row = jdbc.queryForMap("SELECT BIN_TO_UUID(t.code) code, tp.id pid, t.user_id uid, p.number num, p.name pname, p.last_name plast FROM tournament_participants tp " +
                "JOIN tournaments t ON t.id=tp.tournament_id JOIN teams te ON te.id=tp.team_id JOIN players p ON p.team_id=te.id " +
                "WHERE t.name=? AND te.name=? AND (p.status IS NULL OR p.status=1) ORDER BY p.id LIMIT 1", prefix, prefix + " A");
    }

    @AfterEach void clean() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
        jdbc.update("UPDATE tournaments SET is_active=1, is_finished=0 WHERE name=?", prefix);
        jdbc.update("DELETE e FROM events e JOIN matches m ON m.id=e.match_id JOIN match_days md ON md.id=m.match_day_id JOIN tournaments t ON t.id=md.tournament_id WHERE t.name=?", prefix);
        jdbc.update("UPDATE matches m JOIN match_days md ON md.id=m.match_day_id JOIN tournaments t ON t.id=md.tournament_id SET m.is_finished=0, m.mvp_player=NULL WHERE t.name=?", prefix);
        try { if (code != null) tournaments.deleteTournament(code); } catch (Exception ignored) { }
        jdbc.update("DELETE pp FROM player_participants pp JOIN players p ON p.id=pp.player_id JOIN teams t ON t.id=p.team_id WHERE t.name LIKE ?", prefix + "%");
        jdbc.update("DELETE p FROM players p JOIN teams t ON t.id=p.team_id WHERE t.name LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM teams WHERE name LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM tournaments WHERE name=?", prefix);
        String like = "qa.add.%@example.test";
        jdbc.update("DELETE FROM players WHERE team_id IN (SELECT t.id FROM teams t JOIN users u ON u.id = t.user_id WHERE u.email LIKE ?)", like);
        jdbc.update("DELETE FROM teams WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", like);
        SecurityContextHolder.clearContext();
    }

    MvcResult send(String json) throws Exception {
        return mvc.perform(put("/api/v1/team-participant/" + row.get("code") + "/add-player/" + row.get("pid"))
                .header("Authorization", "Bearer " + jwt.getToken(owner, owner.getAuthorities()))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    @Test void datosInvalidosDan400PorValid() throws Exception {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM players", Long.class);
        var r = send("{\"name\":\"A\",\"lastName\":\"B\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":150}");
        assertEquals(400, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM players", Long.class));
    }

    @Test void mismoNombreConOtroDorsalDa400SinReutilizar() throws Exception {
        // C(c): no se suma en silencio al jugador existente con otro dorsal.
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM player_participants", Long.class);
        int other = ((Number) row.get("num")).intValue() == 98 ? 97 : 98;
        var r = send("{\"name\":\"" + row.get("pname") + "\",\"lastName\":\"" + row.get("plast") + "\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":" + other + "}");
        String body = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(400, r.getResponse().getStatus(), body);
        assertTrue(body.contains("Ya existe") && body.contains("dorsal " + row.get("num")), body);
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM player_participants", Long.class));
    }

    @Test void dorsalDeJugadorActivoDa400() throws Exception {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM players", Long.class);
        var r = send("{\"name\":\"Nuevo Http\",\"lastName\":\"Prueba Dorsal\",\"isCaptain\":false,\"isGoalKeeper\":false,\"number\":" + row.get("num") + "}");
        assertEquals(400, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertTrue(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("dorsal " + row.get("num")));
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM players", Long.class));
    }
}
