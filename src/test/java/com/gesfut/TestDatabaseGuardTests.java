package com.gesfut;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Seguridad: los tests solo corren sobre la base aislada gesfut_isolated_test (la inyecta test-isolated.ps1 y la valida IsolatedDbGuard)
 *  (src/test/resources/config/application.properties). */
@SpringBootTest
class TestDatabaseGuardTests {
    @Autowired JdbcTemplate jdbc;

    @Test void siempreLaBaseAislada() {
        assertEquals("gesfut_isolated_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
    }
}
