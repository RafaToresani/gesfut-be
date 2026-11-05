package com.gesfut.config.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Punto 4 (5/11): invalidacion de sesiones por passwordChangedAt con precision de milisegundos. Sin base ni Spring:
 *  clave aleatoria generada en el test (no es la clave real de la app). */
class JwtPasswordChangedAtTests {
    JwtService jwt;
    String key;
    UserDetails user = new User("qa.jwt@example.test", "x", List.of(new SimpleGrantedAuthority("ROLE_USER")));

    @BeforeEach void setUp() {
        byte[] b = new byte[32]; new java.security.SecureRandom().nextBytes(b);
        key = Base64.getEncoder().encodeToString(b);
        jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "SECRET_KEY", key);
    }

    static LocalDateTime utc(long epochMs, long extraNanos) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC).plusNanos(extraNanos);
    }

    @Test void sesionEmitidaEnElMismoSegundoPeroAntesDelCambioQuedaInvalidada() {
        String t = jwt.getToken(user, user.getAuthorities());
        long iatMs = jwt.getIssuedAtEpochMillis(t);
        assertTrue(iatMs > 0, "el token de sesion trae iat_ms");
        assertEquals(iatMs / 1000, jwt.getIssuedAtEpochSeconds(t));
        // Cambio 0,3 ms despues de emitir (mismo milisegundo) y al final del mismo segundo: ambos invalidan.
        assertFalse(jwt.isIssuedAfterPasswordChange(t, utc(iatMs, 300_000)));
        long finDelSegundo = (iatMs / 1000) * 1000 + 999;
        if (finDelSegundo > iatMs) assertFalse(jwt.isIssuedAfterPasswordChange(t, utc(finDelSegundo, 0)));
        assertFalse(jwt.isIssuedAfterPasswordChange(t, utc(iatMs + 1, 0)));
        assertFalse(jwt.isTokenValid(t, conCambio(utc(iatMs, 300_000))), "isTokenValid tambien la rechaza");
    }

    @Test void sesionEmitidaDespuesDelCambioSigueValiendo() {
        String t = jwt.getToken(user, user.getAuthorities());
        long iatMs = jwt.getIssuedAtEpochMillis(t);
        assertTrue(jwt.isIssuedAfterPasswordChange(t, utc(iatMs, 0)), "cambio en el mismo instante exacto: vale");
        assertTrue(jwt.isIssuedAfterPasswordChange(t, utc(iatMs - 1, 700_000)), "cambio 0,3 ms antes de emitir: vale");
        assertTrue(jwt.isIssuedAfterPasswordChange(t, utc(iatMs - 5_000, 0)));
        assertTrue(jwt.isIssuedAfterPasswordChange(t, null), "sin cambio de contrasena: vale");
        assertTrue(jwt.isTokenValid(t, user));
    }

    @Test void tokenViejoSinIatMsConservaLaReglaPorSegundos() {
        long iatMs = (System.currentTimeMillis() / 1000) * 1000 + 100;
        String viejo = Jwts.builder().claims(Map.of(JwtService.TYP_CLAIM, JwtService.TYP_SESSION)).subject(user.getUsername())
                .issuedAt(new Date(iatMs)).expiration(new Date(iatMs + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(key))).compact();
        assertEquals(-1, jwt.getIssuedAtEpochMillis(viejo));
        assertTrue(jwt.isIssuedAfterPasswordChange(viejo, utc(iatMs - 100, 500_000_000)), "mismo segundo: regla vieja");
        assertFalse(jwt.isIssuedAfterPasswordChange(viejo, utc(iatMs - 100 + 1000, 0)), "segundo siguiente: invalida");
    }

    @Test void redondeoDelCambioHaciaArriba() {
        assertEquals(1000, JwtService.changedAtCeilMillis(utc(1000, 0)));
        assertEquals(1001, JwtService.changedAtCeilMillis(utc(1000, 1)));
        assertEquals(1001, JwtService.changedAtCeilMillis(utc(1000, 999_999)));
    }

    private UserDetails conCambio(LocalDateTime changedAt) {
        com.gesfut.models.user.UserEntity u = org.mockito.Mockito.mock(com.gesfut.models.user.UserEntity.class);
        org.mockito.Mockito.when(u.getUsername()).thenReturn(user.getUsername());
        org.mockito.Mockito.when(u.getPasswordChangedAt()).thenReturn(changedAt);
        return u;
    }
}