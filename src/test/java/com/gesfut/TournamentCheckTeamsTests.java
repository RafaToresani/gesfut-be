package com.gesfut;

import org.junit.jupiter.api.Tag;

import com.gesfut.dtos.requests.MatchDayRequest;
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

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Revisar y generar / inicializar: DNI repetido entre equipos e intervalo 0 dan 400 con mensaje claro y no crean nada. */
@Tag("requiere-base-regresion")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
class TournamentCheckTeamsTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TeamService teams;
    @Autowired TournamentService tournaments;
    @Autowired UserRepository users;
    @Autowired com.gesfut.config.security.jwt.JwtService jwt;
    final String prefix = "TEST DNI " + java.util.UUID.randomUUID().toString().substring(0, 8);
    UserEntity owner; String code; long teamA, teamB;

    @BeforeEach void setUp() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        var row = jdbc.queryForMap("SELECT t.user_id uid, BIN_TO_UUID(t.code) code FROM tournaments t WHERE t.code IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tournament_participants p WHERE p.tournament_id=t.id) ORDER BY t.id LIMIT 1");
        owner = users.findById(((Number) row.get("uid")).longValue()).orElseThrow();
        code = (String) row.get("code");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
        teams.createTeam(new TeamRequest(prefix + " A", "#000000", new LinkedHashSet<>(List.of(
                new PlayerRequest("Juan", "Perez", true, true, 10, "30123456"), new PlayerRequest("Pedro", "Gomez", false, false, 1)))));
        teams.createTeam(new TeamRequest(prefix + " B", "#000000", new LinkedHashSet<>(List.of(
                new PlayerRequest("Juancho", "Perez", true, true, 9, "30.123.456"), new PlayerRequest("Luis", "Sosa", false, false, 2)))));
        teamA = jdbc.queryForObject("SELECT id FROM teams WHERE name=?", Long.class, prefix + " A");
        teamB = jdbc.queryForObject("SELECT id FROM teams WHERE name=?", Long.class, prefix + " B");
    }
    @AfterEach void clean() {
        jdbc.update("DELETE p FROM players p JOIN teams t ON t.id=p.team_id WHERE t.name LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM teams WHERE name LIKE ?", prefix + "%");
        SecurityContextHolder.clearContext();
    }
    long participants() { return jdbc.queryForObject("SELECT COUNT(*) FROM tournament_participants p JOIN tournaments t ON t.id=p.tournament_id WHERE BIN_TO_UUID(t.code)=?", Long.class, code); }

    @Test void revisarRechazaDniEnDosEquiposNombrandoAmbos() {
        var ex = assertThrows(IllegalArgumentException.class, () -> tournaments.checkTeams(new MatchDayRequest(code, new ArrayList<>(List.of(teamA, teamB)), null, null, null)));
        assertTrue(ex.getMessage().contains("30123456"), ex.getMessage());
        assertTrue(ex.getMessage().contains(prefix + " A") && ex.getMessage().contains(prefix + " B"), ex.getMessage());
    }

    @Test void inicializarDa400ConMensajeYNoCreaNada() throws Exception {
        String token = "Bearer " + jwt.getToken(owner, owner.getAuthorities());
        String body = "{\"tournamentCode\":\"" + code + "\",\"teams\":[" + teamA + "," + teamB + "]}";
        var r = mvc.perform(post("/api/v1/tournaments/initialize").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(400, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("dos equipos"));
        assertEquals(0, participants());
        var check = mvc.perform(post("/api/v1/tournaments/check-teams").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(400, check.getResponse().getStatus());
    }

    @Test void intervaloCeroDa400ConMensaje() throws Exception {
        String token = "Bearer " + jwt.getToken(owner, owner.getAuthorities());
        jdbc.update("UPDATE players SET dni=NULL WHERE team_id=?", teamB);
        String body = "{\"tournamentCode\":\"" + code + "\",\"teams\":[" + teamA + "," + teamB + "],\"startDate\":\"2030-01-01T10:00:00\",\"minutesPerMatch\":50,\"dayBetweenMatchDay\":0}";
        var r = mvc.perform(post("/api/v1/tournaments/initialize").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(400, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("al menos 1 d"), r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, participants());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities())); // MockMvc limpia el contexto
        assertDoesNotThrow(() -> tournaments.checkTeams(new MatchDayRequest(code, new ArrayList<>(List.of(teamA, teamB)), null, null, null)));
    }
}
