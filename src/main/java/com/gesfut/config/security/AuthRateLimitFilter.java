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
        if (path.startsWith("/api/v1/auth/reset-password/") || isResend(path)) return 3;
        if (path.startsWith("/api/v1/auth/change-password")) return 10;
        return -1;
    }

    static final String RESEND = "/api/v1/auth/resend-verification/";
    static final long RESEND_WINDOW_MS = 3_600_000;
    static final String RESEND_BODY = "/api/v1/auth/resend-verification";
    /** Reenviar mail: nuevo con el email en el body (POST JSON {email}) o alias viejo con el email en la ruta. */
    static boolean isResend(String path) { return path.equals(RESEND_BODY) || path.startsWith(RESEND); }

    /** Reenviar mail: 3 por hora por IP y tambien por cuenta (email), aunque cambie la IP. */
    static long windowFor(String path) { return isResend(path) || isReset(path) ? RESEND_WINDOW_MS : WINDOW_MS; }

    /** A4 #4 (31/10): recuperar contrasena, 3 por hora por IP y tambien por mail (normalizado), contado ANTES de mirar si el mail existe. */
    static final String RESET = "/api/v1/auth/reset-password/";
    static boolean isReset(String path) { return path.startsWith(RESET); }

    /** Mail normalizado (trim + minusculas) para la clave del limite; null si no se puede leer. Nunca se loguea. */
    static String normalizedEmailFromPath(String path, String prefix) {
        try {
            String e = java.net.URLDecoder.decode(path.substring(prefix.length()), java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase(java.util.Locale.ROOT);
            return e.isEmpty() ? null : e;
        } catch (IllegalArgumentException ex) { return null; }
    }

    private boolean hit(String key, int limit, long window, long now) { return limiter.hit(key, limit, window, now); }

    static String bucketFor(String path) {
        int i = path.indexOf('/', "/api/v1/auth/".length());
        return i > 0 ? path.substring(0, i) : path;
    }

    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        boolean local = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        if (local) {
            String cf = request.getHeader("CF-Connecting-IP");
            if (cf != null && !cf.isBlank()) return cf.trim();
        }
        return remote;
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
            String email = normalizedEmailFromPath(path, RESET);
            if (email != null) blocked = hit("reset-email|" + email, limit, window, now);
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
