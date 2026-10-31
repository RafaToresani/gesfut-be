package com.gesfut.services.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * A4 #1 (31/10): tokens de un solo uso para verificar mail y recuperar contrasena.
 * Opacos (32 bytes aleatorios, base64url), NO son JWT: no sirven como sesion.
 * En la base se guarda solo prefijo + SHA-256 en hex (nunca el token en claro).
 */
public final class OneTimeTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private OneTimeTokens() {}

    public static String newRaw() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
