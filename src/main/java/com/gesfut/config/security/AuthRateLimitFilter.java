package com.gesfut.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loop 4: rate limit simple en memoria para login, registro y mails de recuperacion/verificacion.
 * Ventana deslizante por IP + tipo de endpoint. Detras del tunel la IP real llega en CF-Connecting-IP.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final long WINDOW_MS = 60_000;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    static int limitFor(String path) {
        if (path.equals("/api/v1/auth/login")) return 10;
        if (path.equals("/api/v1/auth/sing-up")) return 5;
        if (path.startsWith("/api/v1/auth/reset-password/") || path.startsWith("/api/v1/auth/resend-verification/")) return 3;
        if (path.startsWith("/api/v1/auth/change-password")) return 10;
        return -1;
    }

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
        int limit = "POST".equalsIgnoreCase(request.getMethod()) ? limitFor(path) : -1;
        if (limit < 0) { chain.doFilter(request, response); return; }
        String key = clientIp(request) + "|" + bucketFor(path);
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        boolean blocked;
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) q.pollFirst();
            blocked = q.size() >= limit;
            if (!blocked) q.addLast(now);
        }
        if (hits.size() > 10_000) hits.entrySet().removeIf(e -> { synchronized (e.getValue()) { return e.getValue().isEmpty() || now - e.getValue().peekLast() > WINDOW_MS; } });
        if (blocked) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Demasiados intentos. Esper\u00e1 un minuto y prob\u00e1 de nuevo.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
