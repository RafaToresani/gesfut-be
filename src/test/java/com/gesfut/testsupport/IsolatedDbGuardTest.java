package com.gesfut.testsupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;

/**
 * Tests del EnvironmentPostProcessor con MockEnvironment: no levantan contexto ni conectan.
 */
class IsolatedDbGuardTest {

    private static MockEnvironment validEnv() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:52169/gesfut_isolated_test")
                .withProperty("spring.datasource.username", "gesfut_test_only")
                .withProperty("spring.flyway.clean-disabled", "true")
                .withProperty("GESFUT_ISOLATED_PORT", "52169");
    }

    private static void assertGuard(MockEnvironment env, String fragment) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new IsolatedDbGuard().postProcessEnvironment(env, null));
        assertTrue(e.getMessage().startsWith("GESFUT ISOLATION GUARD: "), e.getMessage());
        if (fragment != null) {
            assertTrue(e.getMessage().contains(fragment), "mensaje sin '" + fragment + "': " + e.getMessage());
        }
    }

    @Test
    void correDespuesDeConfigData() {
        assertEquals(Ordered.LOWEST_PRECEDENCE, new IsolatedDbGuard().getOrder());
    }

    @Test
    void aceptaEntornoValido() {
        assertDoesNotThrow(() -> new IsolatedDbGuard().postProcessEnvironment(validEnv(), null));
    }

    @Test
    void aceptaPerfilTest() {
        MockEnvironment env = validEnv();
        env.setActiveProfiles("test");
        assertDoesNotThrow(() -> new IsolatedDbGuard().postProcessEnvironment(env, null));
    }

    @Test
    void rechazaUrl3306ComoLaDelConfigDeTest() {
        assertGuard(validEnv().withProperty("spring.datasource.url",
                "jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013"), "3306");
    }

    @Test
    void rechazaPlaceholderSinResolver() {
        assertGuard(validEnv().withProperty("spring.datasource.url", "${DB_NAME}"), null);
    }

    @Test
    void rechazaSinUrl() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.username", "gesfut_test_only")
                .withProperty("spring.flyway.clean-disabled", "true")
                .withProperty("GESFUT_ISOLATED_PORT", "52169");
        assertGuard(env, "no está definida");
    }

    @Test
    void rechazaSinPuertoDelScript() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:52169/gesfut_isolated_test")
                .withProperty("spring.datasource.username", "gesfut_test_only")
                .withProperty("spring.flyway.clean-disabled", "true");
        assertGuard(env, "GESFUT_ISOLATED_PORT");
    }

    @Test
    void rechazaPuertoDistintoAlDelScript() {
        assertGuard(validEnv().withProperty("GESFUT_ISOLATED_PORT", "52170"), "no coincide");
    }

    @Test
    void rechazaUsuarioDistinto() {
        assertGuard(validEnv().withProperty("spring.datasource.username", "root"), "gesfut_test_only");
    }

    @Test
    void rechazaBaseDistinta() {
        assertGuard(validEnv().withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:52169/GESFUT2"),
                "gesfut_isolated_test");
    }

    @Test
    void rechazaPerfilLocalActivo() {
        MockEnvironment env = validEnv();
        env.setActiveProfiles("local");
        assertGuard(env, "local");
    }

    @Test
    void rechazaPerfilLocalPorPropiedad() {
        assertGuard(validEnv().withProperty("spring.profiles.active", "test,local"), "local");
    }

    @Test
    void rechazaPerfilIncluido() {
        assertGuard(validEnv().withProperty("spring.profiles.include", "local"), "local");
    }

    @Test
    void rechazaFlywayUrlDistinta() {
        assertGuard(validEnv().withProperty("spring.flyway.url", "jdbc:mysql://127.0.0.1:3306/GESFUT2"), "3306");
    }

    @Test
    void rechazaFlywayUserDistinto() {
        assertGuard(validEnv().withProperty("spring.flyway.user", "root"), "spring.flyway.user");
    }

    @Test
    void exigeFlywayCleanDisabled() {
        assertGuard(validEnv().withProperty("spring.flyway.clean-disabled", "false"), "clean-disabled");
    }

    @Test
    void rechazaHikariJdbcUrlQuePisaLaPrincipal() {
        assertGuard(validEnv().withProperty("spring.datasource.hikari.jdbc-url",
                "jdbc:mysql://127.0.0.1:3306/GESFUT2"), "3306");
    }

    @Test
    void rechazaHikariUsuarioDistinto() {
        assertGuard(validEnv().withProperty("spring.datasource.hikari.username", "root"), null);
    }

    @Test
    void rechazaHikariDataSourceProperties() {
        assertGuard(validEnv().withProperty("spring.datasource.hikari.data-source-properties.socketfactory", "x"),
                "prohibido");
    }

    @Test
    void rechazaDataSourceClassName() {
        assertGuard(validEnv().withProperty("spring.datasource.hikari.data-source-class-name",
                "com.mysql.cj.jdbc.MysqlDataSource"), "data-source-class-name");
    }

    @Test
    void rechazaOtroTipoDeDataSource() {
        assertGuard(validEnv().withProperty("spring.datasource.type",
                "org.springframework.jdbc.datasource.SimpleDriverDataSource"), "Hikari");
    }

    @Test
    void rechazaHikariJndiEnSusDosGrafias() {
        assertGuard(validEnv().withProperty("spring.datasource.hikari.data-source-jndi", "java:comp/env/jdbc/x"),
                "data-source-jndi");
        assertGuard(validEnv().withProperty("spring.datasource.hikari.data-source-j-n-d-i", "java:comp/env/jdbc/x"),
                "data-source-jndi");
    }

    @Test
    void rechazaJndi() {
        assertGuard(validEnv().withProperty("spring.datasource.jndi-name", "java:comp/env/jdbc/x"), "jndi");
    }
}
