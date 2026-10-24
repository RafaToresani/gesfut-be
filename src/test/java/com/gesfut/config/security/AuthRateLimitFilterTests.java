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
}
