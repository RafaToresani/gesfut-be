package com.gesfut;

import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.TournamentService;
import com.gesfut.services.impl.AuthServiceImpl;
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
    "spring.jpa.hibernate.ddl-auto=validate"
})
class ArchivedReadOnlyTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired TournamentService tournaments;
    @Autowired UserRepository users;
    @Autowired AuthServiceImpl authService;
    final String prefix = "TEST ARCH " + java.util.UUID.randomUUID().toString().substring(0, 8);
    final String email = "qa.arch." + java.util.UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    long id; String code, name; Object wasActive; UserEntity owner;

    @BeforeEach void setUp() {
        assertEquals("gesfut_isolated_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        authService.singUp(new RegisterRequest(email, "Qa-" + java.util.UUID.randomUUID(), "QaArch", "User", true));
        owner = users.findByEmail(email).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
        code = tournaments.createTournament(new TournamentRequest(prefix));
        var row = jdbc.queryForMap("SELECT id, name, is_active FROM tournaments WHERE name=?", prefix);
        id = ((Number) row.get("id")).longValue(); name = (String) row.get("name"); wasActive = row.get("is_active");
        jdbc.update("UPDATE tournaments SET is_active=0 WHERE id=?", id);
    }
    @AfterEach void restore() {
        if (id != 0) jdbc.update("UPDATE tournaments SET is_active=1, is_finished=0, name=? WHERE id=?", name, id);
        try { if (code != null) tournaments.deleteTournament(code); } catch (Exception ignored) { }
        jdbc.update("DELETE FROM tournaments WHERE name=?", prefix);
        String like = "qa.arch.%@example.test";
        jdbc.update("DELETE FROM players WHERE team_id IN (SELECT t.id FROM teams t JOIN users u ON u.id = t.user_id WHERE u.email LIKE ?)", like);
        jdbc.update("DELETE FROM teams WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", like);
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
