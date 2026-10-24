package com.gesfut;

import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.TournamentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Bug 6: un torneo archivado es solo lectura tambien para renombrar y sumar equipos/inicializar. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class ArchivedReadOnlyTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired TournamentService tournaments;
    @Autowired UserRepository users;
    long id; String code, name; Object wasActive;

    @BeforeEach void setUp() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        var row = jdbc.queryForMap("SELECT id, user_id uid, BIN_TO_UUID(code) code, name, is_active FROM tournaments WHERE code IS NOT NULL AND COALESCE(is_finished,0)=0 ORDER BY id LIMIT 1");
        id = ((Number) row.get("id")).longValue(); code = (String) row.get("code"); name = (String) row.get("name"); wasActive = row.get("is_active");
        UserEntity owner = users.findById(((Number) row.get("uid")).longValue()).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
        jdbc.update("UPDATE tournaments SET is_active=0 WHERE id=?", id);
    }
    @AfterEach void restore() {
        jdbc.update("UPDATE tournaments SET is_active=?, name=? WHERE id=?", wasActive, name, id);
        SecurityContextHolder.clearContext();
    }

    @Test void renombrarArchivadoFallaConMensaje() {
        var ex = assertThrows(ResponseStatusException.class, () -> tournaments.changeNameTournamentByCode(code, "Otro nombre"));
        assertTrue(ex.getReason().contains("archivado"));
        assertEquals(name, jdbc.queryForObject("SELECT name FROM tournaments WHERE id=?", String.class, id));
    }

    @Test void sumarEquiposArchivadoFalla() {
        var ex = assertThrows(ResponseStatusException.class, () -> tournaments.addTeamsToTournament(code, new ArrayList<>(List.of(-1L))));
        assertTrue(ex.getReason().contains("archivado"));
    }

    @Test void restaurarSigueAndando() {
        assertTrue(tournaments.changeIsActive(code, true));
        assertEquals(1, jdbc.queryForObject("SELECT is_active+0 FROM tournaments WHERE id=?", Integer.class, id));
    }
}
