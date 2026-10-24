package com.gesfut;

import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** PUT /team-participant/{code}/add-player/{id}: @Valid (400) y regla de dorsal activo, por HTTP. No escribe datos. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
class AddPlayerHttpTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired com.gesfut.config.security.jwt.JwtService jwt;
    Map<String, Object> row;
    UserEntity owner;

    @BeforeEach void setUp() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        row = jdbc.queryForMap("SELECT BIN_TO_UUID(t.code) code, tp.id pid, t.user_id uid, p.number num, p.name pname, p.last_name plast FROM tournament_participants tp " +
                "JOIN tournaments t ON t.id=tp.tournament_id JOIN teams te ON te.id=tp.team_id JOIN players p ON p.team_id=te.id " +
                "WHERE te.user_id=t.user_id AND t.code IS NOT NULL AND t.is_active=1 AND t.is_finished=0 AND tp.is_active=1 AND (p.status IS NULL OR p.status=1) AND LOWER(te.name)<>'free' ORDER BY tp.id LIMIT 1");
        owner = users.findById(((Number) row.get("uid")).longValue()).orElseThrow();
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
