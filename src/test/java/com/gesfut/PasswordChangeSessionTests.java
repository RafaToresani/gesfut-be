package com.gesfut;

import org.junit.jupiter.api.Tag;

import com.gesfut.config.security.dtos.LoginRequest;
import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.config.security.jwt.CookieAuth;
import com.gesfut.config.security.jwt.JwtService;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.impl.AuthServiceImpl;
import com.gesfut.services.impl.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** A4 #2 (31/10): cambiar la contrasena invalida las sesiones anteriores; el login nuevo sigue funcionando. Solo base de prueba. */
@Tag("requiere-base-regresion")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate",
    "gesfut.mail.verificacion.enabled=false"
})
@AutoConfigureMockMvc
class PasswordChangeSessionTests {
    @Autowired AuthServiceImpl auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @MockBean EmailService emailService;

    @AfterEach void limpiar() {
        String like = "qa.pwd.%@example.test";
        jdbc.update("DELETE FROM players WHERE team_id IN (SELECT t.id FROM teams t JOIN users u ON u.id = t.user_id WHERE u.email LIKE ?)", like);
        jdbc.update("DELETE FROM teams WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", like);
    }

    String[] cuenta() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        String email = "qa.pwd." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        String pass = "Qa-" + UUID.randomUUID();
        auth.singUp(new RegisterRequest(email, pass, "QaPwd", "Uno", true));
        return new String[]{email, pass};
    }
    String sesion(String email) { UserEntity u = users.findByEmail(email).orElseThrow(); return jwt.getToken(u, u.getAuthorities()); }
    int teams(String t) throws Exception { return mvc.perform(get("/api/v1/teams").header("Authorization", "Bearer " + t)).andReturn().getResponse().getStatus(); }
    String validate(String t) throws Exception {
        return mvc.perform(post("/api/v1/auth/validate-token").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
    String sesionCookie(MockHttpServletResponse r) {
        for (String h : r.getHeaders("Set-Cookie")) if (h.startsWith(CookieAuth.SESSION_COOKIE + "=")) return h.substring(CookieAuth.SESSION_COOKIE.length() + 1, h.indexOf(';'));
        return null;
    }

    @Test void resetDeContrasenaCierraLaSesionAnteriorYElLoginNuevoEntra() throws Exception {
        String[] c = cuenta();
        String vieja = sesion(c[0]);
        assertEquals(200, teams(vieja));
        assertNull(jdbc.queryForObject("SELECT password_changed_at FROM users WHERE email=?", java.sql.Timestamp.class, c[0]));
        var antesUtc = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(2);
        Thread.sleep(1100); // el iat tiene resolucion de segundos: el cambio cae en un segundo posterior
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        auth.resetPasswordSendEmail(c[0]);
        verify(emailService).sendEmail(eq(c[0]), anyString(), body.capture());
        String raw = body.getValue().substring(body.getValue().indexOf("/auth/reset-password#t=") + "/auth/reset-password#t=".length()).trim();
        String nueva = "Qa-" + UUID.randomUUID();
        auth.changePassword(raw, nueva);
        var guardado = users.findByEmail(c[0]).orElseThrow().getPasswordChangedAt();
        assertNotNull(guardado);
        assertTrue(guardado.isAfter(antesUtc) && guardado.isBefore(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(2)), "password_changed_at en UTC");
        assertEquals(401, teams(vieja), "login previo al cambio da 401");
        assertEquals("false", validate(vieja));
        String nuevaSesion = auth.logIn(new LoginRequest(c[0], nueva)).token();
        assertEquals(200, teams(nuevaSesion), "login nuevo (mismo segundo o despues) da 200");
        assertEquals("true", validate(nuevaSesion));
    }

    @Test void cambiarConLaAnteriorCierraOtrasSesionesYRenuevaLaCookieDeEsta() throws Exception {
        String[] c = cuenta();
        String otraSesion = sesion(c[0]);
        String estaSesion = sesion(c[0]);
        Thread.sleep(1100);
        String nueva = "Qa-" + UUID.randomUUID();
        var r = mvc.perform(post("/api/v1/auth/change-password-with-old-password").header("Authorization", "Bearer " + estaSesion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"oldPassword\":\"" + c[1] + "\",\"newPassword\":\"" + nueva + "\"}")).andReturn().getResponse();
        assertEquals(200, r.getStatus());
        assertEquals(401, teams(otraSesion), "la otra sesion previa da 401");
        assertEquals(401, teams(estaSesion), "el token viejo de esta sesion tambien");
        String renovada = sesionCookie(r);
        assertNotNull(renovada, "la respuesta trae una cookie de sesion nueva");
        assertEquals(200, teams(renovada));
        assertEquals(200, teams(auth.logIn(new LoginRequest(c[0], nueva)).token()));
    }

    @Test void sinCambioDeContrasenaLasSesionesSiguenValiendo() throws Exception {
        String[] c = cuenta();
        String s = sesion(c[0]);
        assertEquals(200, teams(s));
        assertEquals("true", validate(s));
        assertTrue(jwt.isIssuedAfterPasswordChange(s, null));
        long iat = jwt.getIssuedAtEpochSeconds(s);
        var iatUtc = java.time.LocalDateTime.ofEpochSecond(iat, 0, java.time.ZoneOffset.UTC);
        long iatMs = jwt.getIssuedAtEpochMillis(s);
        assertTrue(iatMs / 1000 == iat, "iat_ms coincide con el iat en segundos");
        var iatMsUtc = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(iatMs), java.time.ZoneOffset.UTC);
        assertTrue(jwt.isIssuedAfterPasswordChange(s, iatMsUtc.minusNanos(300_000)), "cambio un instante antes de emitir: vale");
        assertFalse(jwt.isIssuedAfterPasswordChange(s, iatMsUtc.plusNanos(300_000)), "cambio un instante despues, mismo segundo: invalida");
        assertTrue(jwt.isIssuedAfterPasswordChange(s, iatUtc.minusSeconds(1)));
        assertFalse(jwt.isIssuedAfterPasswordChange(s, iatUtc.plusSeconds(1)), "cambio en el segundo siguiente: invalida");
    }

    /** Punto 4 (5/11): sesion abierta (usuario logueado) y reset en el MISMO segundo, sin esperar: la sesion previa da 401 y el relogin entra. */
    @Test void resetEnElMismoSegundoQueLaSesionPreviaLaInvalidaYElReloginEntra() throws Exception {
        String[] c = cuenta();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        auth.resetPasswordSendEmail(c[0]);
        verify(emailService).sendEmail(eq(c[0]), anyString(), body.capture());
        String raw = body.getValue().substring(body.getValue().indexOf("/auth/reset-password#t=") + "/auth/reset-password#t=".length()).trim();
        String vieja = sesion(c[0]);
        long iatMs = jwt.getIssuedAtEpochMillis(vieja);
        String nueva = "Qa-" + UUID.randomUUID();
        var r = mvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("token", raw, "password", nueva)))).andReturn().getResponse();
        assertEquals(200, r.getStatus());
        var cambio = users.findByEmail(c[0]).orElseThrow().getPasswordChangedAt();
        long cambioMs = cambio.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        assertTrue(cambioMs >= iatMs, "el cambio es posterior a la emision de la sesion vieja");
        assertEquals(401, teams(vieja), "sesion emitida antes del cambio (aunque sea el mismo segundo) da 401");
        assertEquals("false", validate(vieja));
        String relogin = auth.logIn(new LoginRequest(c[0], nueva)).token();
        assertEquals(200, teams(relogin), "relogin despues del cambio da 200");
        assertEquals("true", validate(relogin));
    }
}
