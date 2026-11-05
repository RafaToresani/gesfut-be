package com.gesfut.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitFilterTests {

    private int post(AuthRateLimitFilter f, String path, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("CF-Connecting-IP", ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void loginIsLimitedPerIpAndOtherIpsAreNotAffected() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        for (int i = 0; i < 10; i++) assertEquals(200, post(f, "/api/v1/auth/login", "1.1.1.1"));
        assertEquals(429, post(f, "/api/v1/auth/login", "1.1.1.1"));
        assertEquals(200, post(f, "/api/v1/auth/login", "2.2.2.2"));
    }

    @Test
    void resetPasswordSharesOneBucketForAllEmails() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        for (int i = 0; i < 20; i++) assertEquals(200, postReset(f, "a" + i + "@x.com", "3.3.3.3", new MockFilterChain()).getStatus(), "pedido " + (i + 1));
        assertEquals(429, postReset(f, "otro@x.com", "3.3.3.3", new MockFilterChain()).getStatus(), "21.o pedido desde la misma IP");
    }

    @Test
    void resetPasswordLimitaPorMailNormalizadoAunqueCambieLaIpYEsPorHora() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        assertEquals(200, postReset(f, "Misma@x.com", "8.8.1.1", new MockFilterChain()).getStatus());
        assertEquals(200, postReset(f, " misma@X.com ", "8.8.1.2", new MockFilterChain()).getStatus());
        assertEquals(200, postReset(f, "misma@x.com", "8.8.1.3", new MockFilterChain()).getStatus());
        assertEquals(429, postReset(f, "MISMA@x.com", "8.8.1.4", new MockFilterChain()).getStatus(), "4.o pedido al mismo mail desde otra IP");
        assertEquals(200, postReset(f, "otra@x.com", "8.8.1.5", new MockFilterChain()).getStatus());
        assertEquals(3_600_000, AuthRateLimitFilter.windowFor("/api/v1/auth/reset-password"));
        assertEquals("a@b.com", AuthRateLimitFilter.normalizeEmail(" A@B.com "));
        assertNull(AuthRateLimitFilter.normalizeEmail("   "));
        assertNull(AuthRateLimitFilter.normalizeEmail(null));
    }

    @Test
    void resetPasswordContadoresAcotados() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        for (int i = 0; i < 25_000; i++) postReset(f, "m" + i + "@x.com", "9." + (i / 65536) + "." + ((i / 256) % 256) + "." + (i % 256), new MockFilterChain());
        assertTrue(f.limiter.size() <= 20_000, "el limitador no crece sin limite: " + f.limiter.size());
    }

    @Test
    void otherEndpointsAreNotLimited() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        for (int i = 0; i < 30; i++) assertEquals(200, post(f, "/api/v1/matches/load-result", "4.4.4.4"));
    }

    @Test
    void resendVerificationIsThreePerHourPerIpAndPerEmail() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        for (int i = 0; i < 3; i++) assertEquals(200, post(f, "/api/v1/auth/resend-verification/a" + i + "@x.com", "5.5.5.5"));
        assertEquals(429, post(f, "/api/v1/auth/resend-verification/otro@x.com", "5.5.5.5"));
        for (int i = 0; i < 3; i++) assertEquals(200, post(f, "/api/v1/auth/resend-verification/misma@x.com", "6.6.6." + i));
        assertEquals(429, post(f, "/api/v1/auth/resend-verification/MISMA@x.com", "7.7.7.7"), "por cuenta aunque cambie la IP");
        assertEquals(3_600_000, AuthRateLimitFilter.windowFor("/api/v1/auth/resend-verification/x@x.com"));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/resend-verification/misma@x.com");
        req.setRemoteAddr("127.0.0.1"); req.addHeader("CF-Connecting-IP", "8.8.8.8");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        assertTrue(res.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("Probaste muchas veces"));
    }

    private MockHttpServletResponse postReset(AuthRateLimitFilter f, String email, String ip, MockFilterChain chain) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/reset-password");
        req.setRemoteAddr("127.0.0.1"); req.addHeader("CF-Connecting-IP", ip);
        req.setContentType("application/json"); req.setContent(("{\"email\":\"" + email + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, chain);
        return res;
    }

    @Test
    void resetConEmailEnElBodyElControllerRecibeElBodyYLasRutasViejasNoSeLimitanComoReset() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        MockFilterChain chain = new MockFilterChain();
        assertEquals(200, postReset(f, "cuerpo@x.com", "9.7.0.1", chain).getStatus());
        String seen = new String(chain.getRequest().getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(seen.contains("cuerpo@x.com"), "el body llega intacto al controller");
        // Body sin email o ilegible: no rompe el filtro, solo cuenta por IP (el controller responde 400).
        MockHttpServletRequest roto = new MockHttpServletRequest("POST", "/api/v1/auth/reset-password");
        roto.setRemoteAddr("127.0.0.1"); roto.addHeader("CF-Connecting-IP", "9.7.0.2");
        roto.setContentType("application/json"); roto.setContent("{no-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse r = new MockHttpServletResponse();
        f.doFilter(roto, r, new MockFilterChain());
        assertEquals(200, r.getStatus());
        // Rutas viejas: ya no son reset ni change-password (no comparten limite ni leen el path).
        assertEquals(-1, AuthRateLimitFilter.limitFor("/api/v1/auth/reset-password/a@x.com"));
        assertEquals(-1, AuthRateLimitFilter.limitFor("/api/v1/auth/change-password/tok"));
        assertEquals(10, AuthRateLimitFilter.limitFor("/api/v1/auth/change-password"));
        assertEquals(20, AuthRateLimitFilter.limitFor("/api/v1/auth/reset-password"));
        assertEquals(10, AuthRateLimitFilter.limitFor("/api/v1/auth/change-password-with-old-password"), "conserva el limite que tenia por prefijo");
    }

    private MockHttpServletResponse postBody(AuthRateLimitFilter f, String email, String ip, MockFilterChain chain) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/resend-verification");
        req.setRemoteAddr("127.0.0.1"); req.addHeader("CF-Connecting-IP", ip);
        req.setContentType("application/json"); req.setContent(("{\"email\":\"" + email + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, chain);
        return res;
    }

    @Test
    void resendConEmailEnElBodyLimitaPorCuentaYElControllerRecibeElBody() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        for (int i = 0; i < 3; i++) {
            MockFilterChain chain = new MockFilterChain();
            assertEquals(200, postBody(f, "body@x.com", "9.9.9." + i, chain).getStatus());
            String seen = new String(chain.getRequest().getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(seen.contains("body@x.com"), "el body llega intacto al controller");
        }
        assertEquals(429, postBody(f, "BODY@x.com", "9.9.8.1", new MockFilterChain()).getStatus(), "por cuenta aunque cambie la IP");
        assertEquals(429, post(f, "/api/v1/auth/resend-verification/body@x.com", "9.9.8.2"), "el alias viejo comparte el cupo");
    }

    @Test
    void memoriaAcotadaYClavesQueVencen() {
        SlidingWindowLimiter l = new SlidingWindowLimiter(100, 60_000);
        long now = 1_000_000;
        for (int i = 0; i < 1_000; i++) l.hit("k" + i, 5, 60_000, now + i);
        assertTrue(l.size() <= 100, "nunca mas de maxKeys: " + l.size());
        l.cleanup(now + 10_000_000);
        assertEquals(0, l.size(), "las vencidas se limpian");
        assertFalse(l.hit("x", 1, 60_000, now));
        assertTrue(l.hit("x", 1, 60_000, now + 1));
        assertFalse(l.hit("x", 1, 60_000, now + 60_002), "la ventana vence");
    }

    /** Punto 5: desde una IP no confiable (no loopback) CF-Connecting-IP se ignora; cuenta la remoteAddr real. */
    private MockHttpServletResponse postResetDesde(AuthRateLimitFilter f, String remote, String cfHeader, String email) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/reset-password");
        req.setRemoteAddr(remote);
        if (cfHeader != null) req.addHeader("CF-Connecting-IP", cfHeader);
        req.setContentType("application/json"); req.setContent(("{\"email\":\"" + email + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void resetPasswordIgnoraCfConnectingIpFalsaDesdeIpNoConfiable() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        // Un cliente directo (203.0.113.7) rota la cabecera en cada pedido: igual comparte un solo cupo de 20.
        for (int i = 0; i < 20; i++) assertEquals(200, postResetDesde(f, "203.0.113.7", "198.51.100." + i, "f" + i + "@x.com").getStatus(), "pedido " + (i + 1));
        assertEquals(429, postResetDesde(f, "203.0.113.7", "198.51.100.200", "f99@x.com").getStatus(), "la cabecera falsa no abre cupo nuevo");
        // La IP falsificada no quedo consumida: desde loopback con esa misma CF-Connecting-IP sigue habiendo cupo.
        assertEquals(200, postResetDesde(f, "127.0.0.1", "198.51.100.0", "g0@x.com").getStatus());
        // IPv6 no confiable: tampoco se cree la cabecera.
        for (int i = 0; i < 20; i++) postResetDesde(f, "2001:db8::5", "198.51.101." + i, "h" + i + "@x.com");
        assertEquals(429, postResetDesde(f, "2001:db8::5", "198.51.101.250", "h99@x.com").getStatus());
    }

    @Test
    void resetPasswordDesdeLoopbackUsaCfConnectingIpYSigueEnTresPorMail() throws Exception {
        for (String lo : new String[]{"127.0.0.1", "::1", "0:0:0:0:0:0:0:1"}) {
            AuthRateLimitFilter f = new AuthRateLimitFilter();
            for (int i = 0; i < 20; i++) assertEquals(200, postResetDesde(f, lo, "192.0.2.1", "l" + i + "@x.com").getStatus());
            assertEquals(429, postResetDesde(f, lo, "192.0.2.1", "lx@x.com").getStatus(), "21.o desde la misma CF-Connecting-IP via " + lo);
            assertEquals(200, postResetDesde(f, lo, "192.0.2.2", "ly@x.com").getStatus(), "otra IP real detras del tunel tiene su cupo");
        }
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        for (int i = 0; i < 3; i++) assertEquals(200, postResetDesde(f, "127.0.0.1", "192.0.2." + (10 + i), "Uno@x.com").getStatus());
        assertEquals(429, postResetDesde(f, "127.0.0.1", "192.0.2.20", " uno@X.com ").getStatus(), "4.o al mismo mail normalizado aunque la IP tenga cupo");
        assertEquals(3, AuthRateLimitFilter.RESET_EMAIL_LIMIT);
    }

    @Test
    void changePasswordSigueEnDiezPorMinutoPorIp() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        for (int i = 0; i < 10; i++) assertEquals(200, post(f, "/api/v1/auth/change-password", "192.0.2.50"));
        assertEquals(429, post(f, "/api/v1/auth/change-password", "192.0.2.50"));
        assertEquals(60_000, AuthRateLimitFilter.windowFor("/api/v1/auth/change-password"));
    }

    /** Tema 3 (5/11): IPv6 agrupada por /64 en la clave de cada bucket por IP. Llega por CF-Connecting-IP desde loopback. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"/api/v1/auth/reset-password,20", "/api/v1/auth/change-password,10", "/api/v1/auth/login,10", "/api/v1/auth/signup,5", "/api/v1/auth/resend-verification,3"})
    void ipv6DelMismoSlash64CompartenBucketYDeOtroSlash64No(String path, int limit) throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        for (int i = 0; i < limit; i++) assertEquals(200, postBodyTo(f, path, "2001:db8:aaaa:1::" + Integer.toHexString(i + 1), "v" + i + "@x.com"), "pedido " + (i + 1));
        assertEquals(429, postBodyTo(f, path, "2001:db8:aaaa:1:ffff:ffff:ffff:fffe", "vz@x.com"), "otra IPv6 del mismo /64 comparte bucket");
        assertEquals(429, postBodyTo(f, path, "2001:DB8:AAAA:0001:0:0:0:99", "vy@x.com"), "misma /64 escrita distinto");
        assertEquals(200, postBodyTo(f, path, "2001:db8:aaaa:2::1", "vx@x.com"), "un /64 distinto tiene su propio bucket");
    }

    @Test
    void ipKeyNormalizaIpv6YDejaIpv4() {
        assertEquals("2001:db8:aaaa:1::/64", AuthRateLimitFilter.ipKey("2001:db8:aaaa:1::5"));
        assertEquals(AuthRateLimitFilter.ipKey("2001:db8:aaaa:1:1:2:3:4"), AuthRateLimitFilter.ipKey("[2001:DB8:AAAA:1::9]"));
        assertNotEquals(AuthRateLimitFilter.ipKey("2001:db8:aaaa:1::1"), AuthRateLimitFilter.ipKey("2001:db8:aaaa:2::1"));
        assertEquals("1.2.3.4", AuthRateLimitFilter.ipKey("1.2.3.4"));
        assertEquals("1.2.3.4", AuthRateLimitFilter.ipKey("::ffff:1.2.3.4"));
        assertEquals("0:0:0:0::/64", AuthRateLimitFilter.ipKey("::1"));
        for (String basura : new String[]{"no:es-ip.example", ".1:2", "abc", "evil.example.com", "", "   ", "1.2.3.4 5.6.7.8", "1.2.3", "256.1.1.1", "01.2.3.4",
                "1:2:3:4:5:6:7:8:9", "1::2::3", "::ffff:1.2.3.4.5", "1.2.3.4::", "fe80::1%eth0", "12345::", "2001:db8::" + "a".repeat(5000)})
            assertNull(AuthRateLimitFilter.ipKey(basura), "no es literal IP: " + (basura.length() > 30 ? basura.substring(0, 30) + "..." : basura));
        assertNull(AuthRateLimitFilter.ipKey(null));
        assertEquals("1:2:3:4::/64", AuthRateLimitFilter.ipKey("1:2:3:4:5:6:7:8"));
        assertEquals("0:0:0:0::/64", AuthRateLimitFilter.ipKey("::"));
        assertEquals("64:ff9b:0:0::/64", AuthRateLimitFilter.ipKey("64:ff9b::1.2.3.4"));
    }

    private int postBodyTo(AuthRateLimitFilter f, String path, String ip, String email) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr("127.0.0.1"); req.addHeader("CF-Connecting-IP", ip);
        req.setContentType("application/json"); req.setContent(("{\"email\":\"" + email + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    /** Tema 5 (5/11): remote ::ffff:127.0.0.1 NO es loopback confiable; la cabecera se ignora (un solo cupo aunque rote). */
    @Test
    void remoteIpv4MappedLoopbackNoEsConfiableYLaCabeceraSeIgnora() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        for (int i = 0; i < 20; i++) assertEquals(200, postResetDesde(f, "::ffff:127.0.0.1", "198.51.102." + i, "m" + i + "@x.com").getStatus(), "pedido " + (i + 1));
        assertEquals(429, postResetDesde(f, "::ffff:127.0.0.1", "198.51.102.200", "m99@x.com").getStatus(), "la cabecera no abre cupo nuevo");
        assertEquals(200, postResetDesde(f, "127.0.0.1", "198.51.102.0", "n0@x.com").getStatus(), "esa CF-Connecting-IP no quedo consumida");
    }

    /** Tema 5 (5/11): CF-Connecting-IP desde loopback que no es literal IP: se ignora y cuenta la IP de remote. */
    @Test
    void cfConnectingIpQueNoEsLiteralSeIgnoraYCuentaRemote() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        String[] basura = {"evil.example.com", "abc", ".1:2", "1.2.3.4 5.6.7.8", "x".repeat(300)};
        for (int i = 0; i < 20; i++) assertEquals(200, postResetDesde(f, "127.0.0.1", basura[i % basura.length], "b" + i + "@x.com").getStatus(), "pedido " + (i + 1));
        assertEquals(429, postResetDesde(f, "127.0.0.1", "otro.example.com", "b99@x.com").getStatus(), "todo el texto raro comparte el cupo de remote");
        assertEquals(429, postResetDesde(f, "127.0.0.1", null, "b98@x.com").getStatus(), "es el mismo cupo que sin cabecera");
        assertEquals(200, postResetDesde(f, "127.0.0.1", "192.0.2.77", "b97@x.com").getStatus(), "una IP literal valida si tiene su cupo");
    }
}
