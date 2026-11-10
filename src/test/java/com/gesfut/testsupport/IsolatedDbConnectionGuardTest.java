package com.gesfut.testsupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Tests del BeanPostProcessor sin contexto ni conexiones: los DataSource se construyen sin pedir
 * getConnection(), y se verifica que el pool de Hikari nunca arranca.
 */
class IsolatedDbConnectionGuardTest {

    private static final String OK_URL = "jdbc:mysql://127.0.0.1:52169/gesfut_isolated_test";

    private final IsolatedDbConnectionGuard guard =
            new IsolatedDbConnectionGuard(new MockEnvironment().withProperty("GESFUT_ISOLATED_PORT", "52169"));

    private static void assertGuard(Runnable r) {
        IllegalStateException e = assertThrows(IllegalStateException.class, r::run);
        assertTrue(e.getMessage().startsWith("GESFUT ISOLATION GUARD: "), e.getMessage());
    }

    private static HikariDataSource hikari(String url, String user) {
        HikariDataSource ds = new HikariDataSource(); // constructor sin argumentos: no abre el pool
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        return ds;
    }

    @Test
    void aceptaHikariValidoSinConectar() {
        try (HikariDataSource ds = hikari(OK_URL, "gesfut_test_only")) {
            assertDoesNotThrow(() -> guard.postProcessBeforeInitialization(ds, "dataSource"));
            assertDoesNotThrow(() -> guard.postProcessAfterInitialization(ds, "dataSource"));
            assertFalse(ds.isRunning(), "el guardián no debe arrancar el pool");
        }
    }

    @Test
    void rechazaHikari3306AntesDeConectar() {
        try (HikariDataSource ds = hikari("jdbc:mysql://127.0.0.1:3306/gesfut_isolated_test", "gesfut_test_only")) {
            assertGuard(() -> guard.postProcessBeforeInitialization(ds, "dataSource"));
            assertGuard(() -> guard.postProcessAfterInitialization(ds, "dataSource"));
            assertFalse(ds.isRunning());
        }
    }

    @Test
    void rechazaHikariUsuarioDistinto() {
        try (HikariDataSource ds = hikari(OK_URL, "root")) {
            assertGuard(() -> guard.postProcessAfterInitialization(ds, "dataSource"));
        }
    }

    @Test
    void rechazaHikariSinUrlAlTerminarLaInicializacion() {
        try (HikariDataSource ds = new HikariDataSource()) {
            assertDoesNotThrow(() -> guard.postProcessBeforeInitialization(ds, "dataSource"));
            assertGuard(() -> guard.postProcessAfterInitialization(ds, "dataSource"));
        }
    }

    @Test
    void rechazaHikariConPropiedadPeligrosa() {
        try (HikariDataSource ds = hikari(OK_URL, "gesfut_test_only")) {
            ds.addDataSourceProperty("autoDeserialize", "true");
            assertGuard(() -> guard.postProcessAfterInitialization(ds, "dataSource"));
        }
    }

    @Test
    void rechazaDriverManagerDataSourceA3306() {
        DataSource ds = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/GESFUT2", "root", "x");
        assertGuard(() -> guard.postProcessAfterInitialization(ds, "otroDataSource"));
    }

    @Test
    void rechazaEnvoltorioConDestinoMalo() {
        DataSource inner = new DriverManagerDataSource("jdbc:mysql://localhost:52169/gesfut_isolated_test",
                "gesfut_test_only", "x");
        DataSource wrapper = new DelegatingDataSource(inner);
        assertGuard(() -> guard.postProcessAfterInitialization(wrapper, "envuelto"));
    }

    @Test
    void rechazaTipoDeDataSourceDesconocido() {
        DataSource unknown = mock(DataSource.class);
        assertGuard(() -> guard.postProcessAfterInitialization(unknown, "raro"));
    }

    @Test
    void ignoraBeansQueNoSonDataSourceNiFlyway() {
        assertDoesNotThrow(() -> guard.postProcessAfterInitialization("cualquier cosa", "otro"));
    }
}
