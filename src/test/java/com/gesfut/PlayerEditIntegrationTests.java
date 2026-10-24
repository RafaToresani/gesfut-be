package com.gesfut;

import com.gesfut.dtos.requests.PlayerEditRequest;
import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.TeamService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Editar jugador: solo el duenio, mismas validaciones que el alta, conserva el jugador (y su historial). */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class PlayerEditIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired TeamService teams;
    @Autowired UserRepository users;
    final String prefix = "TEST EDIT " + System.nanoTime() % 100000;
    long juan, pedro;

    @BeforeEach void setUp() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        UserEntity user = users.findAll().stream().findFirst().orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        teams.createTeam(new TeamRequest(prefix + " A", "#000000", new LinkedHashSet<>(List.of(
                new PlayerRequest("Juan", "Perez", true, false, 10, "30123456"),
                new PlayerRequest("Pedro", "Gomez", false, true, 1)))));
        juan = jdbc.queryForObject("SELECT p.id FROM players p JOIN teams t ON t.id=p.team_id WHERE t.name=? AND p.name='Juan'", Long.class, prefix + " A");
        pedro = jdbc.queryForObject("SELECT p.id FROM players p JOIN teams t ON t.id=p.team_id WHERE t.name=? AND p.name='Pedro'", Long.class, prefix + " A");
    }
    @AfterEach void clean() {
        jdbc.update("DELETE p FROM players p JOIN teams t ON t.id=p.team_id WHERE t.name LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM teams WHERE name LIKE ?", prefix + "%");
        SecurityContextHolder.clearContext();
    }

    @Test void editaYConservaElMismoJugador() {
        var r = teams.editPlayer(pedro, new PlayerEditRequest("Pedro Pablo", "Gomez", 5, "31.222.333"));
        assertEquals(pedro, r.id());
        assertEquals("31222333", r.dni());
        var row = jdbc.queryForMap("SELECT name,number,dni FROM players WHERE id=?", pedro);
        assertEquals("Pedro Pablo", row.get("name")); assertEquals(5, ((Number) row.get("number")).intValue());
        teams.editPlayer(pedro, new PlayerEditRequest("Pedro Pablo", "Gomez", 5, ""));
        assertNull(jdbc.queryForObject("SELECT dni FROM players WHERE id=?", String.class, pedro), "DNI vacio = sin DNI");
    }

    @Test void mismasValidacionesQueElAlta() {
        var dup = assertThrows(IllegalArgumentException.class, () -> teams.editPlayer(pedro, new PlayerEditRequest("Pedro", "Gomez", 1, "30123456")));
        assertTrue(dup.getMessage().contains("DNI 30123456"));
        var num = assertThrows(IllegalArgumentException.class, () -> teams.editPlayer(pedro, new PlayerEditRequest("Pedro", "Gomez", 10, null)));
        assertTrue(num.getMessage().contains("dorsal 10"));
        assertThrows(IllegalArgumentException.class, () -> teams.editPlayer(pedro, new PlayerEditRequest("Pedro", "Gomez", 1, "123")));
        assertThrows(IllegalArgumentException.class, () -> teams.editPlayer(pedro, new PlayerEditRequest("Pe", "Gomez", 1, null)));
        assertEquals("Pedro", jdbc.queryForObject("SELECT name FROM players WHERE id=?", String.class, pedro));
    }

    @Test void otroUsuarioNoPuede() {
        var other = UserEntity.builder().id(-1L).email("other@example.test").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(other, null, List.of()));
        assertThrows(Exception.class, () -> teams.editPlayer(juan, new PlayerEditRequest("Hack", "Hack", 3, null)));
        assertEquals("Juan", jdbc.queryForObject("SELECT name FROM players WHERE id=?", String.class, juan));
    }
}
