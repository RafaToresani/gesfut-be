package com.gesfut.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Loop 4: rate limit simple en memoria para login, registro y mails de recuperacion/verificacion.
 * Ventana deslizante por IP + tipo de endpoint. Detras del tunel la IP real llega en CF-Connecting-IP.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final long WINDOW_MS = 60_000;
    /** Memoria acotada: como mucho 20.000 claves, limpieza de vencidas cada minuto. */
    final SlidingWindowLimiter limiter = new SlidingWindowLimiter(20_000, 60_000);

    static int limitFor(String path) {
        if (path.equals("/api/v1/auth/login")) return 10;
        if (path.equals("/api/v1/auth/signup")) return 5;
        // Punto 5 (5/11): reset-password sube a 20 por hora por IP (wifi compartido de clubes); por mail sigue en 3.
        if (isReset(path)) return RESET_IP_LIMIT;
        if (isResend(path)) return 3;
        // Rutas exactas. change-password-with-old-password conserva el limite que antes tenia por prefijo.
        if (path.equals("/api/v1/auth/change-password") || path.equals("/api/v1/auth/change-password-with-old-password")) return 10;
        return -1;
    }

    static final String RESEND = "/api/v1/auth/resend-verification/";
    static final long RESEND_WINDOW_MS = 3_600_000;
    static final String RESEND_BODY = "/api/v1/auth/resend-verification";
    /** Reenviar mail: nuevo con el email en el body (POST JSON {email}) o alias viejo con el email en la ruta. */
    static boolean isResend(String path) { return path.equals(RESEND_BODY) || path.startsWith(RESEND); }

    /** Reenviar mail: 3 por hora por IP y tambien por cuenta (email), aunque cambie la IP. */
    static long windowFor(String path) { return isResend(path) || isReset(path) ? RESEND_WINDOW_MS : WINDOW_MS; }

    /** A4 #4 (31/10): recuperar contrasena, 3 por hora por IP y tambien por mail (normalizado), contado ANTES de mirar si el mail existe.
     *  A4 #3 (31/10): ruta exacta; el mail llega en el body JSON {email}. */
    static final String RESET = "/api/v1/auth/reset-password";
    static boolean isReset(String path) { return path.equals(RESET); }
    /** Punto 5 (5/11): 20 por hora por IP efectiva (clientIp) y 3 por hora por mail normalizado. */
    static final int RESET_IP_LIMIT = 20;
    static final int RESET_EMAIL_LIMIT = 3;

    /** Mail normalizado (trim + minusculas) para la clave del limite; null si falta. Nunca se loguea. */
    public static String normalizeEmail(String raw) {
        if (raw == null) return null;
        String e = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return e.isEmpty() ? null : e;
    }

    private boolean hit(String key, int limit, long window, long now) { return limiter.hit(key, limit, window, now); }

    static String bucketFor(String path) {
        int i = path.indexOf('/', "/api/v1/auth/".length());
        return i > 0 ? path.substring(0, i) : path;
    }

    /** Tema 3 (5/11): clave de IP para TODOS los buckets por IP. IPv6 se agrupa por su /64 (un cliente suele tener
     *  el /64 entero y podria rotar direcciones); IPv4 y IPv4-mapped (::ffff:a.b.c.d) quedan como la IPv4.
     *  Tema 5 (5/11): parser de literales puro (sin InetAddress, nunca resuelve DNS). Si el texto no es un literal IP
     *  valido devuelve null y el que llama usa la IP de remote. */
    static String ipKey(String ip) {
        byte[] b = parseIpLiteral(ip);
        if (b == null) return null;
        if (b.length == 4) return (b[0] & 0xff) + "." + (b[1] & 0xff) + "." + (b[2] & 0xff) + "." + (b[3] & 0xff);
        boolean mapped = true;
        for (int i = 0; i < 10; i++) if (b[i] != 0) { mapped = false; break; }
        if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) return (b[12] & 0xff) + "." + (b[13] & 0xff) + "." + (b[14] & 0xff) + "." + (b[15] & 0xff);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i += 2) sb.append(Integer.toHexString(((b[i] & 0xff) << 8) | (b[i + 1] & 0xff))).append(':');
        return sb.append(":/64").toString();
    }

    /** Literal IPv4 (a.b.c.d, decimal estricto) o IPv6 (con :: y IPv4 final opcional, entre corchetes opcional) a bytes; null si no es literal. */
    static byte[] parseIpLiteral(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.length() > 2 && s.charAt(0) == '[' && s.charAt(s.length() - 1) == ']') s = s.substring(1, s.length() - 1);
        if (s.isEmpty() || s.length() > 45) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c == ':' || c == '.' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return null;
        }
        if (s.indexOf(':') < 0) return parseIpv4(s);
        return parseIpv6(s);
    }

    private static byte[] parseIpv4(String s) {
        String[] p = s.split("\\.", -1);
        if (p.length != 4) return null;
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            String q = p[i];
            if (q.isEmpty() || q.length() > 3 || (q.length() > 1 && q.charAt(0) == '0')) return null;
            int v = 0;
            for (int j = 0; j < q.length(); j++) { char c = q.charAt(j); if (c < '0' || c > '9') return null; v = v * 10 + (c - '0'); }
            if (v > 255) return null;
            out[i] = (byte) v;
        }
        return out;
    }

    private static byte[] parseIpv6(String s) {
        int dbl = s.indexOf("::");
        if (dbl >= 0 && s.indexOf("::", dbl + 1) >= 0) return null;
        String head = dbl >= 0 ? s.substring(0, dbl) : s;
        String tail = dbl >= 0 ? s.substring(dbl + 2) : "";
        java.util.List<Integer> h = groups(head, dbl < 0 || tail.isEmpty());
        java.util.List<Integer> t = dbl >= 0 ? groups(tail, true) : java.util.List.of();
        if (h == null || t == null) return null;
        if (dbl >= 0 && !head.isEmpty() && head.indexOf('.') >= 0) return null; // IPv4 solo al final
        int n = h.size() + t.size();
        if (dbl < 0 ? n != 8 : n > 7) return null;
        int[] g = new int[8];
        for (int i = 0; i < h.size(); i++) g[i] = h.get(i);
        for (int i = 0; i < t.size(); i++) g[8 - t.size() + i] = t.get(i);
        byte[] out = new byte[16];
        for (int i = 0; i < 8; i++) { out[2 * i] = (byte) (g[i] >> 8); out[2 * i + 1] = (byte) g[i]; }
        return out;
    }

    /** Grupos hex separados por ':'; si allowV4, el ultimo puede ser una IPv4 (cuenta como 2 grupos). "" = lista vacia. */
    private static java.util.List<Integer> groups(String s, boolean allowV4) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (s.isEmpty()) return out;
        String[] p = s.split(":", -1);
        for (int i = 0; i < p.length; i++) {
            String q = p[i];
            if (i == p.length - 1 && q.indexOf('.') >= 0) {
                if (!allowV4) return null;
                byte[] v4 = parseIpv4(q);
                if (v4 == null) return null;
                out.add(((v4[0] & 0xff) << 8) | (v4[1] & 0xff));
                out.add(((v4[2] & 0xff) << 8) | (v4[3] & 0xff));
                continue;
            }
            if (q.isEmpty() || q.length() > 4) return null;
            int v = 0;
            for (int j = 0; j < q.length(); j++) { int d = Character.digit(q.charAt(j), 16); if (d < 0) return null; v = v * 16 + d; }
            out.add(v);
        }
        return out;
    }

    /** IP efectiva ya normalizada (ipKey). CF-Connecting-IP solo se cree desde loopback y solo si es un literal IP valido;
     *  si no, se usa la IP de remote. */
    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        boolean local = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        if (local) {
            String cf = ipKey(request.getHeader("CF-Connecting-IP"));
            if (cf != null) return cf;
        }
        String r = ipKey(remote);
        return r != null ? r : remote;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        // El alias viejo comparte cupo con el nuevo: no duplica el límite.
        if (path.equals("/api/v1/auth/sing-up")) path = "/api/v1/auth/signup";
        int limit = "POST".equalsIgnoreCase(request.getMethod()) ? limitFor(path) : -1;
        if (limit < 0) { chain.doFilter(request, response); return; }
        String key = clientIp(request) + "|" + bucketFor(path);
        long now = System.currentTimeMillis();
        long window = windowFor(path);
        boolean blocked = hit(key, limit, window, now);
        if (!blocked && isResend(path)) {
            String email;
            if (path.startsWith(RESEND)) {
                email = java.net.URLDecoder.decode(path.substring(RESEND.length()), java.nio.charset.StandardCharsets.UTF_8);
            } else {
                // El body se lee una sola vez (max 4 KB) y se vuelve a ofrecer al controller.
                CachedBodyRequest cached = new CachedBodyRequest(request, 4096);
                request = cached;
                email = cached.jsonField("email");
            }
            if (email != null && !email.isBlank()) blocked = hit("email|" + email.trim().toLowerCase(), limit, window, now);
        }
        if (!blocked && isReset(path)) {
            // Igual que resend: el body se lee una sola vez (max 4 KB), se vuelve a ofrecer al controller y nunca se loguea.
            // Body ilegible o sin email: solo cuenta el limite por IP (el controller responde 400).
            CachedBodyRequest cached = new CachedBodyRequest(request, 4096);
            request = cached;
            String email = normalizeEmail(cached.jsonField("email"));
            if (email != null) blocked = hit("reset-email|" + email, RESET_EMAIL_LIMIT, window, now);
        }
        if (blocked) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(window / 1000));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(isResend(path) || isReset(path)
                    ? "{\"error\":\"Probaste muchas veces. Esper\u00e1 un rato e intent\u00e1 de nuevo.\"}"
                    : "{\"error\":\"Demasiados intentos. Esper\u00e1 un minuto y prob\u00e1 de nuevo.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Request con el body ya leido: se puede volver a leer (el controller recibe el JSON intacto). */
    static final class CachedBodyRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] body;
        CachedBodyRequest(HttpServletRequest request, int max) throws IOException {
            super(request);
            this.body = request.getInputStream().readNBytes(max);
        }
        String jsonField(String name) {
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
                return node != null && node.hasNonNull(name) ? node.get(name).asText() : null;
            } catch (Exception e) { return null; }
        }
        @Override public jakarta.servlet.ServletInputStream getInputStream() {
            var in = new java.io.ByteArrayInputStream(body);
            return new jakarta.servlet.ServletInputStream() {
                @Override public int read() { return in.read(); }
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(jakarta.servlet.ReadListener l) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
    }
}
