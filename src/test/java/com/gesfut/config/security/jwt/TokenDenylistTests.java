package com.gesfut.config.security.jwt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenDenylistTests {
    @Test
    void revokedTokenIsDeniedUntilItExpires() {
        TokenDenylist d = new TokenDenylist();
        d.revoke("a.b.c", System.currentTimeMillis() + 60_000);
        assertTrue(d.isRevoked("a.b.c"));
        assertFalse(d.isRevoked("x.y.z"));
        assertFalse(d.isRevoked(null));
    }

    @Test
    void expiredEntriesArePurged() {
        TokenDenylist d = new TokenDenylist();
        d.revoke("old", System.currentTimeMillis() - 1);
        assertFalse(d.isRevoked("old"));
        d.revoke("new", System.currentTimeMillis() + 60_000);
        assertEquals(1, d.size());
    }
}