package com.gesfut;

import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.dtos.requests.PlayerEditRequest;
import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.TeamService;
import com.gesfut.services.impl.AuthServiceImpl;
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
    "spring.jpa.hibernate.ddl-auto=validate"
})
class PlayerEditIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired TeamService teams;
    @Autowired UserRepository users;
    @Autowired AuthServiceImpl auth;
    final String prefix = "TEST EDIT " + java.util.UUID.randomUUID().toString().substring(0, 8);
    final String email = "qa.edit." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    long juan, pedro;

    @BeforeEach void setUp() {
        assertEquals("gesfut_isolated_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        auth.singUp(new RegisterRequest(email, "Qa-" + java.util.UUID.randomUUID(), "QaEdit", "User", true));
        UserEntity user = users.findByEmail(email).orElseThrow();
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
        String like = "qa.edit.%@example.test";
        jdbc.update("DELETE FROM players WHERE team_id IN (SELECT t.id FROM teams t JOIN users u ON u.id = t.user_id WHERE u.email LIKE ?)", like);
        jdbc.update("DELETE FROM teams WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", like);
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

    @Test void altaRechazaDorsalDeJugadorActivo() {
        long teamId = jdbc.queryForObject("SELECT id FROM teams WHERE name=?", Long.class, prefix + " A");
        var dup = assertThrows(IllegalArgumentException.class, () -> teams.addPlayerToTeam(teamId, new PlayerRequest("Carlos", "Diaz", false, false, 10)));
        assertTrue(dup.getMessage().contains("dorsal 10"));
        assertTrue(dup.getMessage().contains("Juan Perez"));
        // Si el que tenia el dorsal esta dado de baja, el dorsal queda libre (igual que en editar).
        jdbc.update("UPDATE players SET status=0 WHERE id=?", juan);
        assertDoesNotThrow(() -> teams.addPlayerToTeam(teamId, new PlayerRequest("Carlos", "Diaz", false, false, 10)));
    }

    @Test void loteRechazaDorsalRepetidoCon400() {
        var ex = assertThrows(IllegalArgumentException.class, () -> teams.createTeam(new TeamRequest(prefix + " B", "#000000", new LinkedHashSet<>(List.of(
                new PlayerRequest("Ana", "Lopez", true, false, 7),
                new PlayerRequest("Bea", "Ruiz", false, true, 7))))));
        assertTrue(ex.getMessage().contains("7"));
    }

    @Test void cualquierAltaRespetaElDorsalActivo(@Autowired com.gesfut.services.PlayerService players) {
        var team = jdbc.queryForObject("SELECT id FROM teams WHERE name=?", Long.class, prefix + " A");
        var t = com.gesfut.models.team.Team.builder().id(team).players(new HashSet<>()).build();
        var ex = assertThrows(IllegalArgumentException.class, () -> players.createPlayer(new PlayerRequest("Carlos", "Diaz", false, false, 1), t));
        assertTrue(ex.getMessage().contains("Pedro Gomez"));
    }

    @Test void otroEquipoPuedeUsarElMismoDorsal() {
        assertDoesNotThrow(() -> teams.createTeam(new TeamRequest(prefix + " C", "#000000", new LinkedHashSet<>(List.of(
                new PlayerRequest("Luis", "Sosa", true, false, 10),
                new PlayerRequest("Raul", "Vera", false, true, 1))))));
        long teamC = jdbc.queryForObject("SELECT id FROM teams WHERE name=?", Long.class, prefix + " C");
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM players WHERE team_id=? AND number IN (1,10)", Long.class, teamC));
    }

    @Test void loteContraJugadoresExistentesDelEquipo(@Autowired com.gesfut.services.PlayerService players) {
        long teamId = jdbc.queryForObject("SELECT id FROM teams WHERE name=?", Long.class, prefix + " A");
        var t = com.gesfut.models.team.Team.builder().id(teamId).players(new HashSet<>()).build();
        // El lote valida la lista y cada alta valida contra la base: el 10 (Juan, activo) choca, el 11 no.
        players.validatePlayers(new LinkedHashSet<>(List.of(new PlayerRequest("Ale", "Paz", true, true, 11), new PlayerRequest("Beto", "Gil", false, false, 10))));
        assertDoesNotThrow(() -> players.createPlayer(new PlayerRequest("Ale", "Paz", false, false, 11), t));
        var ex = assertThrows(IllegalArgumentException.class, () -> players.createPlayer(new PlayerRequest("Beto", "Gil", false, false, 10), t));
        assertTrue(ex.getMessage().contains("Juan Perez"));
        // Dado de baja: su dorsal queda libre tambien para este camino.
        jdbc.update("UPDATE players SET status=0 WHERE id=?", juan);
        assertDoesNotThrow(() -> players.createPlayer(new PlayerRequest("Beto", "Gil", false, false, 10), t));
    }

    @Test void otroUsuarioNoPuede() {
        var other = UserEntity.builder().id(-1L).email("other@example.test").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(other, null, List.of()));
        assertThrows(Exception.class, () -> teams.editPlayer(juan, new PlayerEditRequest("Hack", "Hack", 3, null)));
        assertEquals("Juan", jdbc.queryForObject("SELECT name FROM players WHERE id=?", String.class, juan));
    }
}
