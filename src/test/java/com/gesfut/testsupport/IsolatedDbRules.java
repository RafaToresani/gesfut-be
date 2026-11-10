package com.gesfut.testsupport;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Reglas PURAS (sin Spring) del aislamiento de los tests de integración de Gesfut.
 *
 * <p>Todo lo que define "destino seguro" está FIJO EN CÓDIGO: host, rango de puertos, base y usuario.
 * Ninguna variable de entorno puede relajar estas reglas. La única entrada externa es
 * {@code GESFUT_ISOLATED_PORT}, que el script test-isolated.ps1 setea con el puerto real del contenedor;
 * se usa SOLO como chequeo adicional (tiene que coincidir con el puerto de la URL), nunca para aflojar nada.
 *
 * <p>Los mensajes de error nunca incluyen la URL completa, contraseñas ni valores de parámetros:
 * solo el motivo, y a lo sumo el puerto o el nombre del parámetro.
 */
public final class IsolatedDbRules {

    /** Prefijo único y reconocible de todos los rechazos. */
    public static final String PREFIX = "GESFUT ISOLATION GUARD: ";

    // ---- Invariantes fijos (no configurables) ----
    public static final String HOST = "127.0.0.1";
    public static final int FORBIDDEN_PORT = 3306;
    public static final int MIN_PORT = 49152;   // rango efímero donde Docker publica el puerto
    public static final int MAX_PORT = 65535;
    public static final String DATABASE = "gesfut_isolated_test";
    public static final String USERNAME = "gesfut_test_only";
    public static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    public static final String HIKARI_TYPE = "com.zaxxer.hikari.HikariDataSource";

    /** Variable que setea el script con el puerto real del contenedor (chequeo adicional). */
    public static final String EXPECTED_PORT_PROPERTY = "GESFUT_ISOLATED_PORT";

    private static final String SCHEME = "jdbc:mysql://";

    /** Parámetros explícitamente prohibidos (comparación en minúsculas). */
    static final Set<String> FORBIDDEN_PARAMS = Set.of(
            "socketfactory",
            "allowloadlocalinfile",
            "allowloadlocalinfileinpath",
            "autodeserialize",
            "allowurlinlocalinfile",
            "propertiestransform",
            "user",
            "password",
            "password1", "password2", "password3",
            "queryinterceptors",
            "exceptioninterceptor",
            "connectionlifecycleinterceptors",
            "authenticationplugins",
            "defaultauthenticationplugin",
            "clientcertificatekeystoreurl",
            "trustcertificatekeystoreurl",
            "serverrsapublickeyfile",
            "failoverreadonly",
            "autoreconnect",
            "ha.loadbalancestrategy");

    /** Allowlist corta: cualquier parámetro fuera de esta lista se rechaza. Clave en minúsculas → nombre canónico. */
    static final Map<String, String> ALLOWED_PARAMS = Map.of(
            "usessl", "useSSL",
            "allowpublickeyretrieval", "allowPublicKeyRetrieval",
            "servertimezone", "serverTimezone",
            "characterencoding", "characterEncoding");

    private static final Set<String> BOOLEAN_PARAMS = Set.of("usessl", "allowpublickeyretrieval");
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9_./+-]{1,64}");
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern DIGITS = Pattern.compile("[0-9]{1,5}");

    private IsolatedDbRules() {
    }

    /** Resultado de validar una URL: puerto efectivo y forma normalizada (sin secretos). */
    public record ValidatedUrl(int port, String normalized) {
    }

    /** Construye la excepción con el mensaje reconocible. */
    public static IllegalStateException violation(String reason) {
        return new IllegalStateException(PREFIX + reason);
    }

    /** true si la excepción (o alguna causa) viene del guardián. */
    public static boolean isGuardViolation(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof IllegalStateException && c.getMessage() != null && c.getMessage().startsWith(PREFIX)) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // URL JDBC
    // ------------------------------------------------------------------------------------------

    /**
     * Parsea a mano y valida una URL JDBC completa. Formato único aceptado:
     * {@code jdbc:mysql://127.0.0.1:<puerto>/gesfut_isolated_test[?param=valor&...]}.
     *
     * @param rawUrl URL a validar (puede ser null)
     * @param what   nombre de la propiedad/origen, solo para el mensaje
     */
    public static ValidatedUrl validateJdbcUrl(String rawUrl, String what) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            throw violation(what + " no está definida");
        }
        // Caracteres: solo ASCII imprimible sin espacios; nada de userinfo, fragmentos, escapes ni ';'.
        for (int i = 0; i < rawUrl.length(); i++) {
            char c = rawUrl.charAt(i);
            if (c <= 0x20 || c >= 0x7f) {
                throw violation(what + " contiene espacios, caracteres de control o no ASCII");
            }
            if (c == '@' || c == '#' || c == '%' || c == '\\' || c == ';' || c == '"' || c == '\'') {
                throw violation(what + " contiene un carácter no permitido ('" + c + "')");
            }
        }
        if (!rawUrl.startsWith(SCHEME)) {
            if (rawUrl.regionMatches(true, 0, "jdbc:mysql", 0, 10)) {
                throw violation(what + " usa una variante de esquema no permitida "
                        + "(solo jdbc:mysql:// exacto; nada de loadbalance/replication/srv/mayúsculas)");
            }
            throw violation(what + " no usa el esquema jdbc:mysql://");
        }
        String rest = rawUrl.substring(SCHEME.length());

        int slash = rest.indexOf('/');
        int question = rest.indexOf('?');
        if (slash < 0 || (question >= 0 && question < slash)) {
            throw violation(what + " no indica la base (/" + DATABASE + ")");
        }
        String authority = rest.substring(0, slash);
        String pathAndQuery = rest.substring(slash + 1);

        // ---- authority: host:puerto únicos ----
        if (authority.isEmpty()) {
            throw violation(what + " no tiene host");
        }
        if (authority.indexOf(',') >= 0) {
            throw violation(what + " es multi-host/failover (coma en el host)");
        }
        if (authority.indexOf('(') >= 0 || authority.indexOf(')') >= 0 || authority.indexOf('=') >= 0) {
            throw violation(what + " usa sintaxis address=(...)/clave-valor en el host");
        }
        if (authority.indexOf('[') >= 0 || authority.indexOf(']') >= 0) {
            throw violation(what + " usa un host IPv6 (no permitido, tampoco [::1])");
        }
        int colons = countChar(authority, ':');
        if (colons > 1) {
            throw violation(what + " usa un host IPv6 (no permitido)");
        }
        if (colons == 0) {
            throw violation(what + " no indica puerto explícito");
        }
        int colon = authority.indexOf(':');
        String host = authority.substring(0, colon);
        String portText = authority.substring(colon + 1);
        if ("localhost".equalsIgnoreCase(host)) {
            throw violation(what + " usa localhost; tiene que ser exactamente " + HOST);
        }
        if (!HOST.equals(host)) {
            throw violation(what + " no apunta exactamente a " + HOST);
        }
        if (!DIGITS.matcher(portText).matches()) {
            throw violation(what + " tiene un puerto ausente o inválido");
        }
        int port = Integer.parseInt(portText);
        if (port == FORBIDDEN_PORT) {
            throw violation(what + " apunta al puerto 3306 (MySQL80 nativo con datos reales)");
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            throw violation(what + " usa el puerto " + port + ", fuera del rango publicado "
                    + MIN_PORT + "-" + MAX_PORT);
        }

        // ---- base ----
        int q = pathAndQuery.indexOf('?');
        String db = q < 0 ? pathAndQuery : pathAndQuery.substring(0, q);
        String query = q < 0 ? null : pathAndQuery.substring(q + 1);
        if (db.indexOf('/') >= 0) {
            throw violation(what + " tiene una ruta con más de un segmento");
        }
        if (!DATABASE.equals(db)) {
            throw violation(what + " no usa exactamente la base " + DATABASE);
        }

        // ---- parámetros ----
        Map<String, String> canonical = new TreeMap<>();
        if (query != null) {
            if (query.isEmpty()) {
                throw violation(what + " termina en '?' sin parámetros");
            }
            for (String part : query.split("&", -1)) {
                if (part.isEmpty()) {
                    throw violation(what + " tiene un parámetro vacío ('&&' o '&' final)");
                }
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    throw violation(what + " tiene un parámetro sin '=valor'");
                }
                String key = part.substring(0, eq);
                String value = part.substring(eq + 1);
                String canon = checkParam(key, value, what);
                if (canonical.containsKey(canon)) {
                    throw violation(what + " repite el parámetro " + canon);
                }
                canonical.put(canon, value);
            }
        }

        StringBuilder normalized = new StringBuilder(SCHEME).append(HOST).append(':').append(port)
                .append('/').append(DATABASE);
        char sep = '?';
        for (Map.Entry<String, String> e : canonical.entrySet()) {
            normalized.append(sep).append(e.getKey()).append('=').append(e.getValue());
            sep = '&';
        }
        return new ValidatedUrl(port, normalized.toString());
    }

    /**
     * Valida un parámetro de conexión (de la URL o de las dataSourceProperties de Hikari).
     *
     * @return el nombre canónico del parámetro
     */
    public static String checkParam(String key, String value, String what) {
        if (key == null || key.isEmpty()) {
            throw violation(what + " tiene un parámetro sin nombre");
        }
        String shown = SAFE_NAME.matcher(key).matches() ? key : "(nombre inválido)";
        String lower = key.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_PARAMS.contains(lower)) {
            throw violation(what + " usa el parámetro prohibido " + shown);
        }
        String canon = ALLOWED_PARAMS.get(lower);
        if (canon == null) {
            throw violation(what + " usa el parámetro " + shown + ", que no está en la allowlist "
                    + ALLOWED_PARAMS.values());
        }
        if (value == null || !SAFE_VALUE.matcher(value).matches()) {
            throw violation(what + " tiene un valor inválido para " + canon);
        }
        if (BOOLEAN_PARAMS.contains(lower) && !("true".equals(value) || "false".equals(value))) {
            throw violation(what + " tiene un valor no booleano para " + canon);
        }
        return canon;
    }

    /** Valida un mapa de propiedades de driver (por ejemplo, las dataSourceProperties de Hikari). */
    public static void validateDriverProperties(Map<?, ?> props, String what) {
        if (props == null || props.isEmpty()) {
            return;
        }
        for (Map.Entry<?, ?> e : props.entrySet()) {
            checkParam(String.valueOf(e.getKey()), e.getValue() == null ? null : String.valueOf(e.getValue()), what);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Resto de reglas
    // ------------------------------------------------------------------------------------------

    /** El puerto que pasó el script tiene que existir y coincidir con el de la URL. Nunca relaja nada. */
    public static void validateExpectedPort(String expected, int actualPort) {
        if (expected == null || expected.isEmpty()) {
            throw violation(EXPECTED_PORT_PROPERTY + " no está definido: los tests de integración se corren "
                    + "solo con scripts/aislamiento/test-isolated.ps1");
        }
        if (!DIGITS.matcher(expected).matches()) {
            throw violation(EXPECTED_PORT_PROPERTY + " no es un número de puerto válido");
        }
        if (Integer.parseInt(expected) != actualPort) {
            throw violation("el puerto de la URL no coincide con " + EXPECTED_PORT_PROPERTY
                    + " (puerto verificado por el script)");
        }
    }

    /** Usuario exacto. No se muestra el valor recibido. */
    public static void validateUsername(String user, String what) {
        if (user == null || user.isEmpty()) {
            throw violation(what + " no tiene usuario definido");
        }
        if (!USERNAME.equals(user)) {
            throw violation(what + " no usa exactamente el usuario " + USERNAME);
        }
    }

    /** Driver: vacío (Boot lo deduce) o exactamente el de MySQL Connector/J. */
    public static void validateDriverClassName(String driver, String what) {
        if (driver != null && !driver.isEmpty() && !MYSQL_DRIVER.equals(driver)) {
            throw violation(what + " usa un driver distinto de " + MYSQL_DRIVER);
        }
    }

    /** Perfiles: solo vacío o "test". Acepta listas separadas por coma. */
    public static void validateProfiles(Collection<String> profiles, String what) {
        if (profiles == null) {
            return;
        }
        for (String entry : profiles) {
            if (entry == null) {
                continue;
            }
            for (String p : entry.split(",", -1)) {
                String t = p.trim();
                if (!t.isEmpty() && !"test".equals(t)) {
                    String shown = SAFE_NAME.matcher(t).matches() ? t : "(nombre inválido)";
                    throw violation(what + " incluye el perfil '" + shown + "'; solo se permite vacío o 'test'");
                }
            }
        }
    }

    /** spring.flyway.clean-disabled tiene que ser exactamente true. */
    public static void validateCleanDisabled(String value) {
        if (value == null || !"true".equalsIgnoreCase(value.trim())) {
            throw violation("spring.flyway.clean-disabled tiene que ser true");
        }
    }

    /** Valida destino completo (URL + puerto esperado + usuario). Lo usan el guardián y el control de conexión. */
    public static ValidatedUrl validateTarget(String url, String user, String expectedPort, String what) {
        ValidatedUrl v = validateJdbcUrl(url, what + " (url)");
        validateExpectedPort(expectedPort, v.port());
        validateUsername(user, what + " (usuario)");
        return v;
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
