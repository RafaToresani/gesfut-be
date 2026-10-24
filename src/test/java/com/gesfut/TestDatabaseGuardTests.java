package com.gesfut;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Seguridad: sin override propio, los tests caen en la base de regresion aunque DB_NAME apunte a otra
 *  (src/test/resources/config/application.properties). */
@SpringBootTest
class TestDatabaseGuardTests {
    @Autowired JdbcTemplate jdbc;

    @Test void siempreLaBaseDeRegresion() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
    }
}