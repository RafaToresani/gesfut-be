package com.gesfut;

import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.TeamService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Loop 11: importar varios equipos es todo o nada (antes un equipo invalido dejaba creados los anteriores). */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class TeamBatchAtomicityTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired TeamService teams;
    @Autowired UserRepository users;
    final String prefix = "TEST ATOM " + System.nanoTime() % 100000;

    @BeforeEach void auth() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        UserEntity user = users.findAll().stream().findFirst().orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
    @AfterEach void clean() {
        jdbc.update("DELETE p FROM players p JOIN teams t ON t.id=p.team_id WHERE t.name LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM teams WHERE name LIKE ?", prefix + "%");
        SecurityContextHolder.clearContext();
    }
    Set<PlayerRequest> players(boolean captain) {
        return new LinkedHashSet<>(List.of(new PlayerRequest("Juan", "Perez", captain, true, 1), new PlayerRequest("Pedro", "Gomez", false, false, 2)));
    }
    int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM teams WHERE name LIKE ?", Integer.class, prefix + "%"); }

    @Test void unEquipoInvalidoNoDejaCreadosLosAnteriores() {
        var batch = List.of(new TeamRequest(prefix + " A", "#000000", players(true)), new TeamRequest(prefix + " B", "#000000", players(false)));
        assertThrows(IllegalArgumentException.class, () -> teams.createMultipleTeams(batch));
        assertEquals(0, count());
    }
    @Test void unLoteValidoCreaTodos() {
        teams.createMultipleTeams(List.of(new TeamRequest(prefix + " C", "#000000", players(true)), new TeamRequest(prefix + " D", "#FFFFFF", players(true))));
        assertEquals(2, count());
    }
}
