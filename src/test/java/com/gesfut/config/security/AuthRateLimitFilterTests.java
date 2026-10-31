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
        for (int i = 0; i < 3; i++) assertEquals(200, post(f, "/api/v1/auth/reset-password/a" + i + "@x.com", "3.3.3.3"));
        assertEquals(429, post(f, "/api/v1/auth/reset-password/otro@x.com", "3.3.3.3"));
    }

    @Test
    void resetPasswordLimitaPorMailNormalizadoAunqueCambieLaIpYEsPorHora() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        assertEquals(200, post(f, "/api/v1/auth/reset-password/Misma@x.com", "8.8.1.1"));
        assertEquals(200, post(f, "/api/v1/auth/reset-password/%20misma@X.com%20", "8.8.1.2"));
        assertEquals(200, post(f, "/api/v1/auth/reset-password/misma@x.com", "8.8.1.3"));
        assertEquals(429, post(f, "/api/v1/auth/reset-password/MISMA@x.com", "8.8.1.4"), "4.o pedido al mismo mail desde otra IP");
        assertEquals(200, post(f, "/api/v1/auth/reset-password/otra@x.com", "8.8.1.5"));
        assertEquals(3_600_000, AuthRateLimitFilter.windowFor("/api/v1/auth/reset-password/x@x.com"));
        assertEquals("a@b.com", AuthRateLimitFilter.normalizedEmailFromPath("/api/v1/auth/reset-password/%20A@B.com%20", AuthRateLimitFilter.RESET));
        assertNull(AuthRateLimitFilter.normalizedEmailFromPath("/api/v1/auth/reset-password/%zz", AuthRateLimitFilter.RESET));
    }

    @Test
    void resetPasswordContadoresAcotados() throws Exception {
        AuthRateLimitFilter f = new AuthRateLimitFilter();
        for (int i = 0; i < 25_000; i++) post(f, "/api/v1/auth/reset-password/m" + i + "@x.com", "9." + (i / 65536) + "." + ((i / 256) % 256) + "." + (i % 256));
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
}
