package com.gesfut.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Revision critica #14: publico solo lo que cuelga de un codigo de torneo. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
class PublicAccessTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    int status(String url) throws Exception { return mvc.perform(get(url)).andReturn().getResponse().getStatus(); }

    @Test void porIdNumericoYaNoEsPublico() throws Exception {
        for (String url : new String[]{"/api/v1/teams/1", "/api/v1/teams/1/tournaments", "/api/v1/teams/1/players-stats",
                "/api/v1/matches/1", "/api/v1/matches/detailed/1", "/api/v1/team-participant/teams/1"}) {
            int s = status(url);
            assertTrue(s == 401 || s == 403, url + " devolvio " + s);
        }
    }

    @Test void laVistaPublicaDelTorneoSigueAndando() throws Exception {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        String code = jdbc.queryForObject("SELECT BIN_TO_UUID(code) FROM tournaments WHERE code IS NOT NULL ORDER BY id LIMIT 1", String.class);
        for (String url : new String[]{"/api/v1/tournaments/" + code, "/api/v1/team-participant/" + code + "/teams",
                "/api/v1/match-days/" + code, "/api/v1/tournaments/" + code + "/top-scorers"}) {
            assertEquals(200, status(url), url);
        }
    }
}
