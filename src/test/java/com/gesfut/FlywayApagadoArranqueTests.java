package com.gesfut;

import org.junit.jupiter.api.Tag;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A4 #7 fixup: con Flyway apagado (default) un arranque normal NO migra; con ddl-auto=validate solo arranca si la base ya tiene V2. */
@Tag("requiere-base-regresion")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayApagadoArranqueTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSourceProperties ds;
    @Autowired org.springframework.core.env.Environment env;

    @Test void flywayApagadoPorDefecto() {
        assertEquals("false", env.getProperty("spring.flyway.enabled"), "sin FLYWAY_ENABLED, Flyway no corre");
        assertEquals("true", env.getProperty("spring.flyway.clean-disabled"));
    }

    @Test void conFlywayApagadoNoArrancaSobreUnaBaseSinV2YNoLaMigra() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        String name = "gesfut_codex_flyway_v1_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.execute("CREATE DATABASE `" + name + "`");
        try {
            String url = "jdbc:mysql://127.0.0.1:3306/" + name;
            var v1 = new DriverManagerDataSource(url, ds.getUsername(), ds.getPassword());
            Flyway.configure().dataSource(v1).locations("classpath:db/migration").target("1").load().migrate();
            var t = new JdbcTemplate(v1);
            int historiaAntes = t.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);

            Exception ex = assertThrows(Exception.class, () -> new SpringApplicationBuilder(GesfutApplication.class)
                    .web(WebApplicationType.NONE)
                    // como argumentos de linea de comandos (maxima prioridad, igual que un arranque real con overrides)
                    .run("--spring.datasource.url=" + url, "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=false").close(), "con la base en V1 y Flyway apagado, validate debe frenar el arranque");
            String causa = rootMessage(ex);
            assertTrue(causa.contains("password_changed_at") || causa.contains("accepted_terms_at"), "falla por la columna de V2: " + causa);

            assertEquals(historiaAntes, t.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class), "no migro nada");
            assertEquals(0, t.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='password_changed_at'", Integer.class));
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS `" + name + "`");
        }
    }

    static String rootMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) sb.append(c.getMessage()).append(" | ");
        return sb.toString();
    }
}
