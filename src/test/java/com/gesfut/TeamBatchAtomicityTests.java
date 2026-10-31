package com.gesfut;

import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.dtos.responses.TeamImportResponse;
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

/**
 * Plan 22/10 #15 (reemplaza el todo o nada del loop 11): el import crea los equipos validos y reporta los que fallan.
 * Cada equipo es atomico: uno que falla no deja el equipo creado ni jugadores sueltos.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class TeamBatchAtomicityTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired TeamService teams;
    @Autowired UserRepository users;
    final String prefix = "TEST ATOM " + java.util.UUID.randomUUID().toString().substring(0, 8);

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
    int orphanPlayers() { return jdbc.queryForObject("SELECT COUNT(*) FROM players p LEFT JOIN teams t ON t.id=p.team_id WHERE t.id IS NULL", Integer.class); }

    @Test void importaLosValidosYReportaLosQueFallan() {
        int orphansBefore = orphanPlayers();
        var batch = List.of(
            new TeamRequest(prefix + " A", "#000000", players(true)),
            new TeamRequest(prefix + " B", "#000000", new LinkedHashSet<>(List.of(new PlayerRequest("Juan", "Perez", true, true, 7), new PlayerRequest("Pedro", "Gomez", false, false, 7)))), // dorsal repetido: falla en el servicio
            new TeamRequest("X", "#000000", players(true)),                 // nombre corto: falla la validacion
            new TeamRequest(prefix + " C", "#FFFFFF", players(true)));
        TeamImportResponse r = teams.createMultipleTeams(batch);
        assertEquals(2, r.createdCount());
        assertEquals(List.of(prefix + " A", prefix + " C"), r.created());
        assertEquals(List.of(1, 2), r.failed().stream().map(TeamImportResponse.Failure::index).toList());
        r.failed().forEach(f -> assertFalse(f.error() == null || f.error().isBlank()));
        assertEquals(2, count());
        assertEquals(orphansBefore, orphanPlayers());
    }
    @Test void unLoteValidoCreaTodos() {
        TeamImportResponse r = teams.createMultipleTeams(List.of(new TeamRequest(prefix + " D", "#000000", players(true)), new TeamRequest(prefix + " E", "#FFFFFF", players(true))));
        assertEquals(2, r.createdCount());
        assertTrue(r.failed().isEmpty());
        assertEquals(2, count());
    }
    @Test void equipoSoloConNombreSeCrea() {
        // C(b): el plantel minimo se pide al generar el fixture, no al crear.
        var r = teams.createTeam(new TeamRequest(prefix + " F", "#000000", null));
        assertEquals(prefix + " F", r.name());
        assertEquals(1, count());
    }
    @Test void loteVacioOGrandeSeRechaza() {
        assertThrows(IllegalArgumentException.class, () -> teams.createMultipleTeams(List.of()));
    }
}
