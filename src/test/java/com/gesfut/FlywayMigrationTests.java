package com.gesfut;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A4 #7: las migraciones crean desde cero el mismo esquema que la base de regresion, y la regresion quedo con baseline. Nunca GESFUT2. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayMigrationTests {
    static final String REGRESSION = "gesfut_codex_regression_20261013";
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSourceProperties ds;

    static final String COLS = "SELECT CONCAT_WS('|', LOWER(table_name), column_name, column_type, is_nullable) FROM information_schema.columns "
            + "WHERE table_schema=? AND LOWER(table_name) <> 'flyway_schema_history' ORDER BY LOWER(table_name), column_name";

    @Test void regresionQuedoConBaselineYMigrada() {
        assertEquals(REGRESSION, jdbc.queryForObject("SELECT DATABASE()", String.class));
        List<String> versions = jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank", String.class);
        assertEquals("1", versions.get(0), "baseline en V1 (no se ejecuto V1 sobre la base existente)");
        assertEquals("BASELINE", jdbc.queryForObject("SELECT type FROM flyway_schema_history WHERE version='1'", String.class));
    }

    @Test void baseVaciaMigraAlMismoEsquemaQueLaRegresion() {
        assertEquals(REGRESSION, jdbc.queryForObject("SELECT DATABASE()", String.class));
        String name = "gesfut_codex_flyway_vacia_" + UUID.randomUUID().toString().substring(0, 8);
        assertTrue(name.startsWith("gesfut_codex_"), "solo bases de prueba");
        jdbc.execute("CREATE DATABASE `" + name + "`");
        try {
            var vacia = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/" + name, ds.getUsername(), ds.getPassword());
            var result = Flyway.configure().dataSource(vacia).locations("classpath:db/migration").load().migrate();
            assertTrue(result.success);
            assertTrue(result.migrationsExecuted >= 1);
            assertEquals(jdbc.queryForList(COLS, String.class, REGRESSION), jdbc.queryForList(COLS, String.class, name),
                    "mismas tablas y columnas que la base de regresion (validada por Hibernate)");
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS `" + name + "`");
        }
    }
}
