package com.gesfut.config.security;

import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.TournamentService;
import com.gesfut.services.impl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Revision critica #14: publico solo lo que cuelga de un codigo de torneo. */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
class PublicAccessTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthServiceImpl authService;
    @Autowired UserRepository users;
    @Autowired TournamentService tournaments;
    final String prefix = "TEST PUB " + java.util.UUID.randomUUID().toString().substring(0, 8);
    final String email = "qa.pub." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    String code;

    @BeforeEach void setUp() {
        assertEquals("gesfut_isolated_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        authService.singUp(new RegisterRequest(email, "Qa-" + java.util.UUID.randomUUID(), "QaPub", "User", true));
        UserEntity owner = users.findByEmail(email).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
        code = tournaments.createTournament(new TournamentRequest(prefix));
    }

    @AfterEach void clean() {
        try { if (code != null) tournaments.deleteTournament(code); } catch (Exception ignored) { }
        jdbc.update("DELETE FROM tournaments WHERE name=?", prefix);
        String like = "qa.pub.%@example.test";
        jdbc.update("DELETE FROM players WHERE team_id IN (SELECT t.id FROM teams t JOIN users u ON u.id = t.user_id WHERE u.email LIKE ?)", like);
        jdbc.update("DELETE FROM teams WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", like);
        SecurityContextHolder.clearContext();
    }

    int status(String url) throws Exception { return mvc.perform(get(url)).andReturn().getResponse().getStatus(); }

    @Test void porIdNumericoYaNoEsPublico() throws Exception {
        for (String url : new String[]{"/api/v1/teams/1", "/api/v1/teams/1/tournaments", "/api/v1/teams/1/players-stats",
                "/api/v1/matches/1", "/api/v1/matches/detailed/1", "/api/v1/team-participant/teams/1"}) {
            int s = status(url);
            assertTrue(s == 401 || s == 403, url + " devolvio " + s);
        }
    }

    @Test void laVistaPublicaDelTorneoSigueAndando() throws Exception {
        assertEquals("gesfut_isolated_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        for (String url : new String[]{"/api/v1/tournaments/" + code, "/api/v1/team-participant/" + code + "/teams",
                "/api/v1/match-days/" + code, "/api/v1/tournaments/" + code + "/top-scorers"}) {
            assertEquals(200, status(url), url);
        }
    }
}
