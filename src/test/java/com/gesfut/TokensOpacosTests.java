package com.gesfut;

import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.config.security.jwt.CookieAuth;
import com.gesfut.config.security.jwt.JwtService;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.services.impl.AuthServiceImpl;
import com.gesfut.services.impl.EmailService;
import com.gesfut.services.impl.OneTimeTokens;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** A4 #1 (31/10): tokens de reset y verificacion opacos, hasheados, de un solo uso y que no sirven como sesion. Solo base de prueba. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate",
    "gesfut.mail.verificacion.enabled=true"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class TokensOpacosTests {
    @Autowired AuthServiceImpl auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean EmailService emailService;
    @Value("${SECRET.KEY}") String secretKey;

    String guard() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        return "qa.tok." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    /** Registra (captura el link de verificacion) y devuelve {email, tokenVerificacion}. */
    String[] registrar() {
        String email = guard();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        auth.singUp(new RegisterRequest(email, "Qa-" + UUID.randomUUID(), "QaTok", "Uno"));
        verify(emailService, atLeastOnce()).sendEmail(eq(email), anyString(), body.capture());
        return new String[]{email, after(body.getValue(), "/auth/verify-email/")};
    }

    String pedirReset(String email) {
        clearInvocations(emailService);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        auth.resetPasswordSendEmail(email);
        verify(emailService).sendEmail(eq(email), anyString(), body.capture());
        return after(body.getValue(), "/auth/reset-password/");
    }

    static String after(String s, String marker) { return s.substring(s.indexOf(marker) + marker.length()).trim(); }

    String stored(String email) { return jdbc.queryForObject("SELECT reset_token FROM users WHERE email=?", String.class, email); }

    int statusBearer(String t) throws Exception {
        return mvc.perform(get("/api/v1/teams").header("Authorization", "Bearer " + t)).andReturn().getResponse().getStatus();
    }
    int statusCookie(String t) throws Exception {
        return mvc.perform(get("/api/v1/teams").cookie(new Cookie(CookieAuth.SESSION_COOKIE, t))).andReturn().getResponse().getStatus();
    }
    String validate(String t) throws Exception {
        return mvc.perform(post("/api/v1/auth/validate-token").header("Authorization", "Bearer " + t)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test void tokensSonOpacosHasheadosYNoSirvenComoSesion() throws Exception {
        String[] r = registrar();
        String verifyTok = r[1];
        assertEquals(43, verifyTok.length(), "32 bytes en base64url");
        assertEquals(0, verifyTok.chars().filter(c -> c == '.').count(), "no es un JWT");
        assertEquals("verify:" + OneTimeTokens.hash(verifyTok), stored(r[0]));
        assertFalse(stored(r[0]).contains(verifyTok), "en la base no queda el token en claro");
        assertEquals(401, statusBearer(verifyTok));
        assertEquals(401, statusCookie(verifyTok));
        assertEquals("false", validate(verifyTok));

        auth.verifyEmail(verifyTok);
        String resetTok = pedirReset(r[0]);
        assertEquals("reset:" + OneTimeTokens.hash(resetTok), stored(r[0]));
        assertFalse(stored(r[0]).contains(resetTok));
        assertEquals(401, statusBearer(resetTok));
        assertEquals(401, statusCookie(resetTok));
        assertEquals("false", validate(resetTok));
    }

    @Test void jwtFirmadoSinTypSessionSeRechaza() throws Exception {
        guard();
        String email = jdbc.queryForObject("SELECT email FROM users WHERE email_verified=1 ORDER BY id LIMIT 1", String.class);
        String sinTyp = Jwts.builder().subject(email).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey))).compact();
        assertEquals(401, statusBearer(sinTyp));
        assertEquals(401, statusCookie(sinTyp));
        assertEquals("false", validate(sinTyp));
    }

    @Test void prefijosNoSeCruzanYUsoUnicoYVencido() {
        String[] r = registrar();
        assertThrows(ResourceNotFoundException.class, () -> auth.changePassword(r[1], "NuevaClave-123"), "verificacion no sirve como reset");
        auth.verifyEmail(r[1]);
        assertThrows(IllegalArgumentException.class, () -> auth.verifyEmail(r[1]), "verificacion reusada");
        String resetTok = pedirReset(r[0]);
        assertThrows(IllegalArgumentException.class, () -> auth.verifyEmail(resetTok), "reset no sirve como verificacion");
        auth.changePassword(resetTok, "NuevaClave-123");
        assertThrows(ResourceNotFoundException.class, () -> auth.changePassword(resetTok, "OtraClave-456"), "reset reusado");
        assertNull(stored(r[0]));

        String vencido = pedirReset(r[0]);
        jdbc.update("UPDATE users SET reset_token_expiration=? WHERE email=?", LocalDateTime.now().minusMinutes(1), r[0]);
        assertThrows(IllegalStateException.class, () -> auth.changePassword(vencido, "NuevaClave-789"));
    }

    @Test void resetVenceEn30Minutos() {
        String[] r = registrar();
        auth.verifyEmail(r[1]);
        pedirReset(r[0]);
        var exp = jdbc.queryForObject("SELECT reset_token_expiration FROM users WHERE email=?", java.sql.Timestamp.class, r[0]).toLocalDateTime();
        assertTrue(exp.isAfter(LocalDateTime.now().plusMinutes(29)) && exp.isBefore(LocalDateTime.now().plusMinutes(31)));
    }

    @Test void dosUsosSimultaneosSoloUnoGana() throws Exception {
        String[] r = registrar();
        auth.verifyEmail(r[1]);
        String tok = pedirReset(r[0]);
        int n = 4;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(), fail = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            int k = i;
            pool.submit(() -> {
                try { start.await(); auth.changePassword(tok, "Concurrente-" + k + "-xyz"); ok.incrementAndGet(); }
                catch (RuntimeException | InterruptedException e) { fail.incrementAndGet(); }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(1, ok.get());
        assertEquals(n - 1, fail.get());
    }

    @Test void mailInexistenteMismaRespuestaYLogSinTokenNiMail(CapturedOutput out) throws Exception {
        String[] r = registrar();
        auth.verifyEmail(r[1]);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        doThrow(new MailAuthenticationException("auth failed")).when(emailService).sendEmail(anyString(), anyString(), anyString());
        var existe = mvc.perform(post("/api/v1/auth/reset-password/" + r[0]).header("CF-Connecting-IP", "10.8.0.1")).andReturn().getResponse();
        var nadie = mvc.perform(post("/api/v1/auth/reset-password/nadie-" + UUID.randomUUID() + "@example.test").header("CF-Connecting-IP", "10.8.0.2")).andReturn().getResponse();
        assertEquals(existe.getStatus(), nadie.getStatus());
        assertEquals(200, existe.getStatus());
        assertEquals(nadie.getContentAsString(StandardCharsets.UTF_8), existe.getContentAsString(StandardCharsets.UTF_8));
        verify(emailService, atLeastOnce()).sendEmail(eq(r[0]), anyString(), body.capture());
        String tok = after(body.getValue(), "/auth/reset-password/");
        assertTrue(out.getAll().contains("No se pudo enviar el mail de recuperacion"));
        assertFalse(out.getAll().contains(tok), "el log no contiene el token");
        assertFalse(out.getAll().contains(r[0]), "el log no contiene el mail");
        assertFalse(out.getAll().contains(r[1]), "el log no contiene el token de verificacion");
    }
}
