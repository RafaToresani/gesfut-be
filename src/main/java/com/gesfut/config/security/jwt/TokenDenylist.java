package com.gesfut.config.security.jwt;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loop 5: tokens revocados por logout. Se guarda el SHA-256 del token (nunca el token) hasta su vencimiento.
 * En memoria: un reinicio del backend la vacia (los tokens igual vencen a las 24 h).
 */
@Component
public class TokenDenylist {
    private final Map<String, Long> revoked = new ConcurrentHashMap<>();

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void revoke(String token, long expiresAtMillis) {
        purge();
        revoked.put(hash(token), expiresAtMillis);
    }

    public boolean isRevoked(String token) {
        if (token == null) return false;
        Long exp = revoked.get(hash(token));
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) { revoked.remove(hash(token)); return false; }
        return true;
    }

    void purge() {
        long now = System.currentTimeMillis();
        revoked.entrySet().removeIf(e -> e.getValue() < now);
    }

    int size() { return revoked.size(); }
}
