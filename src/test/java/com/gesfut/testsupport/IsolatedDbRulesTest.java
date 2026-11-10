package com.gesfut.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests unitarios PUROS de las reglas: no levantan Spring ni abren conexiones.
 * Todos los casos negativos exigen el prefijo "GESFUT ISOLATION GUARD".
 */
class IsolatedDbRulesTest {

    private static final String OK_URL = "jdbc:mysql://127.0.0.1:52169/gesfut_isolated_test";

    private static IllegalStateException assertGuard(Executable ex, String fragment) {
        IllegalStateException e = assertThrows(IllegalStateException.class, ex);
        assertTrue(e.getMessage().startsWith("GESFUT ISOLATION GUARD: "), e.getMessage());
        if (fragment != null) {
            assertTrue(e.getMessage().contains(fragment), "mensaje sin '" + fragment + "': " + e.getMessage());
        }
        return e;
    }

    private static void assertUrlRejected(String url, String fragment) {
        assertGuard(() -> IsolatedDbRules.validateJdbcUrl(url, "url"), fragment);
    }

    // ---------------- positivos ----------------

    @Test
    void urlValidaSinParametros() {
        IsolatedDbRules.ValidatedUrl v = IsolatedDbRules.validateJdbcUrl(OK_URL, "url");
        assertEquals(52169, v.port());
        assertEquals(OK_URL, v.normalized());
    }

    @Test
    void urlValidaConParametrosDeAllowlistNormalizada() {
        IsolatedDbRules.ValidatedUrl v = IsolatedDbRules.validateJdbcUrl(
                OK_URL + "?serverTimezone=America/Argentina/Buenos_Aires&USESSL=false&allowPublicKeyRetrieval=true",
                "url");
        assertEquals(OK_URL + "?allowPublicKeyRetrieval=true&serverTimezone=America/Argentina/Buenos_Aires&useSSL=false",
                v.normalized());
    }

    @Test
    void destinoCompletoValido() {
        IsolatedDbRules.validateTarget(OK_URL, "gesfut_test_only", "52169", "test");
        IsolatedDbRules.validateProfiles(List.of("test"), "perfiles");
        IsolatedDbRules.validateProfiles(List.of(""), "perfiles");
        IsolatedDbRules.validateCleanDisabled("true");
        IsolatedDbRules.validateDriverClassName(null, "driver");
        IsolatedDbRules.validateDriverClassName("com.mysql.cj.jdbc.Driver", "driver");
    }

    // ---------------- puerto ----------------

    @Test
    void rechazaPuerto3306() {
        assertUrlRejected("jdbc:mysql://127.0.0.1:3306/gesfut_isolated_test", "3306");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "80", "33060", "49151", "65536", "99999"})
    void rechazaPuertoFueraDeRango(String port) {
        assertUrlRejected("jdbc:mysql://127.0.0.1:" + port + "/gesfut_isolated_test", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://127.0.0.1/gesfut_isolated_test",
            "jdbc:mysql://127.0.0.1:/gesfut_isolated_test",
            "jdbc:mysql://127.0.0.1:abc/gesfut_isolated_test",
            "jdbc:mysql://127.0.0.1:123456/gesfut_isolated_test"})
    void rechazaPuertoAusenteOInvalido(String url) {
        assertUrlRejected(url, null);
    }

    @Test
    void exigePuertoDelScriptYQueCoincida() {
        assertGuard(() -> IsolatedDbRules.validateExpectedPort(null, 52169), "GESFUT_ISOLATED_PORT");
        assertGuard(() -> IsolatedDbRules.validateExpectedPort("", 52169), "GESFUT_ISOLATED_PORT");
        assertGuard(() -> IsolatedDbRules.validateExpectedPort("52170", 52169), "no coincide");
        assertGuard(() -> IsolatedDbRules.validateExpectedPort("x", 52169), null);
    }

    // ---------------- host ----------------

    @Test
    void rechazaLocalhost() {
        assertUrlRejected("jdbc:mysql://localhost:52169/gesfut_isolated_test", "localhost");
        assertUrlRejected("jdbc:mysql://LOCALHOST:52169/gesfut_isolated_test", "localhost");
    }

    @Test
    void rechazaIpv6Loopback() {
        assertUrlRejected("jdbc:mysql://[::1]:52169/gesfut_isolated_test", "IPv6");
        assertUrlRejected("jdbc:mysql://::1:52169/gesfut_isolated_test", "IPv6");
        assertUrlRejected("jdbc:mysql://[fe80::1]:52169/gesfut_isolated_test", "IPv6");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://127.0.0.2:52169/gesfut_isolated_test",
            "jdbc:mysql://0.0.0.0:52169/gesfut_isolated_test",
            "jdbc:mysql://127.000.0.1:52169/gesfut_isolated_test",
            "jdbc:mysql://host.docker.internal:52169/gesfut_isolated_test"})
    void rechazaOtrosHosts(String url) {
        assertUrlRejected(url, "127.0.0.1");
    }

    @Test
    void rechazaMultiHost() {
        assertUrlRejected("jdbc:mysql://127.0.0.1:52169,127.0.0.1:3306/gesfut_isolated_test", "multi-host");
    }

    @Test
    void rechazaFailover() {
        assertUrlRejected("jdbc:mysql://127.0.0.1:52169,127.0.0.1:52170/gesfut_isolated_test?failOverReadOnly=false",
                "multi-host");
        assertUrlRejected(OK_URL + "?failOverReadOnly=false", "prohibido");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql:loadbalance://127.0.0.1:52169/gesfut_isolated_test",
            "jdbc:mysql:replication://127.0.0.1:52169/gesfut_isolated_test",
            "jdbc:mysql+srv://127.0.0.1:52169/gesfut_isolated_test",
            "JDBC:MYSQL://127.0.0.1:52169/gesfut_isolated_test"})
    void rechazaVariantesDeEsquema(String url) {
        assertUrlRejected(url, "esquema");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mariadb://127.0.0.1:52169/gesfut_isolated_test",
            "mysqlx://127.0.0.1:52169/gesfut_isolated_test",
            "jdbc:h2:mem:test"})
    void rechazaOtrosEsquemas(String url) {
        assertUrlRejected(url, null);
    }

    @Test
    void rechazaSintaxisAddress() {
        assertUrlRejected("jdbc:mysql://address=(host=127.0.0.1)(port=3306)/gesfut_isolated_test", null);
        assertUrlRejected("jdbc:mysql://(host=127.0.0.1,port=3306)/gesfut_isolated_test", null);
    }

    @Test
    void rechazaUserinfoSinFiltrarSecretos() {
        IllegalStateException e = assertGuard(() -> IsolatedDbRules.validateJdbcUrl(
                "jdbc:mysql://gesfut_test_only:SuperSecreta@127.0.0.1:52169/gesfut_isolated_test", "url"), null);
        assertFalse(e.getMessage().contains("SuperSecreta"));
    }

    @Test
    void rechazaEspaciosYVacios() {
        assertGuard(() -> IsolatedDbRules.validateJdbcUrl(null, "url"), "no está definida");
        assertGuard(() -> IsolatedDbRules.validateJdbcUrl("", "url"), "no está definida");
        assertUrlRejected(" " + OK_URL, "espacios");
        assertUrlRejected(OK_URL + " ", "espacios");
    }

    // ---------------- base ----------------

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://127.0.0.1:52169/GESFUT2",
            "jdbc:mysql://127.0.0.1:52169/gesfut_codex_regression_20261013",
            "jdbc:mysql://127.0.0.1:52169/GESFUT_ISOLATED_TEST",
            "jdbc:mysql://127.0.0.1:52169/",
            "jdbc:mysql://127.0.0.1:52169/gesfut_isolated_test/otra"})
    void rechazaBaseDistinta(String url) {
        assertUrlRejected(url, null);
    }

    @Test
    void rechazaSinBase() {
        assertUrlRejected("jdbc:mysql://127.0.0.1:52169", "base");
        assertUrlRejected("jdbc:mysql://127.0.0.1:52169?useSSL=false", "base");
    }

    // ---------------- usuario ----------------

    @Test
    void rechazaUsuarioDistinto() {
        assertGuard(() -> IsolatedDbRules.validateUsername("root", "u"), "gesfut_test_only");
        assertGuard(() -> IsolatedDbRules.validateUsername("GESFUT_TEST_ONLY", "u"), null);
        assertGuard(() -> IsolatedDbRules.validateUsername(null, "u"), null);
        assertGuard(() -> IsolatedDbRules.validateTarget(OK_URL, "otro", "52169", "t"), null);
    }

    @Test
    void mensajeDeUsuarioNoMuestraElValor() {
        IllegalStateException e = assertGuard(() -> IsolatedDbRules.validateUsername("usuario_real_x", "u"), null);
        assertFalse(e.getMessage().contains("usuario_real_x"));
    }

    // ---------------- parámetros ----------------

    @Test
    void rechazaSocketFactory() {
        assertUrlRejected(OK_URL + "?socketFactory=com.x.Evil", "socketFactory");
    }

    @Test
    void rechazaAllowLoadLocalInfileEnCualquierCaso() {
        assertUrlRejected(OK_URL + "?allowLoadLocalInfile=true", "prohibido");
        assertUrlRejected(OK_URL + "?ALLOWLOADLOCALINFILE=true", "prohibido");
        assertUrlRejected(OK_URL + "?allowLoadLocalInfileInPath=/tmp", "prohibido");
        assertUrlRejected(OK_URL + "?allowUrlInLocalInfile=true", "prohibido");
    }

    @Test
    void rechazaAutoDeserialize() {
        assertUrlRejected(OK_URL + "?autoDeserialize=true", "autoDeserialize");
    }

    @Test
    void rechazaPropertiesTransform() {
        assertUrlRejected(OK_URL + "?propertiesTransform=com.x.T", "prohibido");
    }

    @Test
    void rechazaUserYPasswordEnLaUrl() {
        assertUrlRejected(OK_URL + "?user=root", "prohibido");
        IllegalStateException e = assertGuard(
                () -> IsolatedDbRules.validateJdbcUrl(OK_URL + "?password=SuperSecreta", "url"), "prohibido");
        assertFalse(e.getMessage().contains("SuperSecreta"));
    }

    @Test
    void rechazaParametroDesconocido() {
        assertUrlRejected(OK_URL + "?foo=bar", "allowlist");
        assertUrlRejected(OK_URL + "?useSSL=false&cacheServerConfiguration=true", "allowlist");
    }

    @Test
    void rechazaParametrosMalFormados() {
        assertUrlRejected(OK_URL + "?", null);
        assertUrlRejected(OK_URL + "?useSSL", null);
        assertUrlRejected(OK_URL + "?useSSL=false&&serverTimezone=UTC", null);
        assertUrlRejected(OK_URL + "?useSSL=false&useSSL=true", "repite");
        assertUrlRejected(OK_URL + "?useSSL=quizas", "booleano");
        assertUrlRejected(OK_URL + "?serverTimezone=UTC;x", null);
    }

    @Test
    void rechazaPropiedadesDeDriverPeligrosas() {
        assertGuard(() -> IsolatedDbRules.validateDriverProperties(Map.of("socketFactory", "x"), "props"), "prohibido");
        assertGuard(() -> IsolatedDbRules.validateDriverProperties(Map.of("foo", "bar"), "props"), "allowlist");
        IsolatedDbRules.validateDriverProperties(Map.of("useSSL", "false"), "props");
    }

    // ---------------- perfiles, flyway y driver ----------------

    @Test
    void rechazaPerfilLocal() {
        assertGuard(() -> IsolatedDbRules.validateProfiles(List.of("local"), "perfiles"), "local");
        assertGuard(() -> IsolatedDbRules.validateProfiles(List.of("test,local"), "perfiles"), "local");
        assertGuard(() -> IsolatedDbRules.validateProfiles(List.of("test", "prod"), "perfiles"), "prod");
    }

    @Test
    void exigeCleanDisabled() {
        assertGuard(() -> IsolatedDbRules.validateCleanDisabled("false"), "clean-disabled");
        assertGuard(() -> IsolatedDbRules.validateCleanDisabled(null), "clean-disabled");
    }

    @Test
    void rechazaOtroDriver() {
        assertGuard(() -> IsolatedDbRules.validateDriverClassName("org.h2.Driver", "driver"), null);
    }
}
