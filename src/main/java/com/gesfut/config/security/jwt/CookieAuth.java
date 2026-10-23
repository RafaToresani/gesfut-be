package com.gesfut.config.security.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Plan 22/10 #13: la sesion viaja en una cookie httpOnly (el JS del navegador no puede leerla).
 * CSRF por doble envio: cookie XSRF-TOKEN legible + header X-XSRF-TOKEN en metodos que escriben.
 */
public final class CookieAuth {
    public static final String SESSION_COOKIE = "GESFUT_SESSION";
    public static final String XSRF_COOKIE = "XSRF-TOKEN";
    public static final String XSRF_HEADER = "X-XSRF-TOKEN";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CookieAuth() {}

    public static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) return c.getValue();
        return null;
    }

    public static String sessionToken(HttpServletRequest request) {
        return readCookie(request, SESSION_COOKIE);
    }

    /** Token de la cookie o, para clientes de API/Swagger, del header Authorization. */
    public static String anyToken(HttpServletRequest request) {
        String t = sessionToken(request);
        if (t != null) return t;
        String h = request.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) return h.substring(7);
        return null;
    }

    public static boolean isUnsafe(String method) {
        return !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method) || "TRACE".equals(method));
    }

    public static boolean csrfOk(HttpServletRequest request) {
        if (!isUnsafe(request.getMethod())) return true;
        String cookie = readCookie(request, XSRF_COOKIE);
        String header = request.getHeader(XSRF_HEADER);
        if (cookie == null || header == null) return false;
        return MessageDigest.isEqual(cookie.getBytes(), header.getBytes());
    }

    private static boolean secure(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }

    public static void issue(HttpServletRequest request, HttpServletResponse response, String token, Duration ttl) {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        String xsrf = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        boolean secure = secure(request);
        response.addHeader("Set-Cookie", ResponseCookie.from(SESSION_COOKIE, token)
                .httpOnly(true).secure(secure).sameSite("Strict").path("/api").maxAge(ttl).build().toString());
        response.addHeader("Set-Cookie", ResponseCookie.from(XSRF_COOKIE, xsrf)
                .httpOnly(false).secure(secure).sameSite("Strict").path("/").maxAge(ttl).build().toString());
    }

    public static void clear(HttpServletRequest request, HttpServletResponse response) {
        boolean secure = secure(request);
        response.addHeader("Set-Cookie", ResponseCookie.from(SESSION_COOKIE, "")
                .httpOnly(true).secure(secure).sameSite("Strict").path("/api").maxAge(0).build().toString());
        response.addHeader("Set-Cookie", ResponseCookie.from(XSRF_COOKIE, "")
                .httpOnly(false).secure(secure).sameSite("Strict").path("/").maxAge(0).build().toString());
    }
}
