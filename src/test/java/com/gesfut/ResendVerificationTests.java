package com.gesfut;

import org.junit.jupiter.api.Tag;

import com.gesfut.services.impl.AuthServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Reenviar mail: respuesta neutral, enlace que vence y reenvio que invalida el anterior. Solo base de prueba. */
@Tag("requiere-base-regresion")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate",
    "gesfut.mail.verificacion.enabled=true"
})
@AutoConfigureMockMvc
class ResendVerificationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthServiceImpl auth;
    Map<String, Object> unverified; String verifiedEmail;

    @BeforeEach void setUp() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        unverified = jdbc.queryForMap("SELECT id, email, reset_token, reset_token_expiration FROM users WHERE email_verified=0 ORDER BY id LIMIT 1");
        verifiedEmail = jdbc.queryForObject("SELECT email FROM users WHERE email_verified=1 ORDER BY id LIMIT 1", String.class);
    }
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("mailExecutor") org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor mailExecutor;

    /** El reenvio corre en otro hilo: se espera a que termine antes de mirar o restaurar la base. */
    void awaitMail() throws InterruptedException {
        for (int i = 0; i < 100 && (mailExecutor.getActiveCount() > 0 || !mailExecutor.getThreadPoolExecutor().getQueue().isEmpty()); i++) Thread.sleep(50);
    }

    @AfterEach void restore() throws InterruptedException {
        awaitMail();
        jdbc.update("UPDATE users SET reset_token=?, reset_token_expiration=?, email_verified=0 WHERE id=?", unverified.get("reset_token"), unverified.get("reset_token_expiration"), unverified.get("id"));
    }

    String[] resend(String email, String ip) throws Exception {
        var r = mvc.perform(post("/api/v1/auth/resend-verification/" + email).header("CF-Connecting-IP", ip)).andReturn().getResponse();
        return new String[]{String.valueOf(r.getStatus()), r.getContentAsString(StandardCharsets.UTF_8)};
    }

    @Test void mismaRespuestaExistaOnoYesteVerificadaOno() throws Exception {
        var a = resend("nadie-" + System.nanoTime() + "@example.test", "10.0.0.1");
        var b = resend(verifiedEmail, "10.0.0.2");
        var c = resend((String) unverified.get("email"), "10.0.0.3");
        assertEquals("200", a[0]); assertArrayEquals(a, b); assertArrayEquals(a, c);
        assertTrue(a[1].contains("Si el mail existe y falta verificarlo"));
    }

    @Test void conEmailEnElBodyMismaRespuestaYElReenvioOcurreEnSegundoPlano() throws Exception {
        jdbc.update("UPDATE users SET reset_token=?, reset_token_expiration=? WHERE id=?", "verify:tok-body", LocalDateTime.now().plusHours(5), unverified.get("id"));
        var neutral = resend("nadie-" + System.nanoTime() + "@example.test", "10.0.1.1");
        var r = mvc.perform(post("/api/v1/auth/resend-verification").header("CF-Connecting-IP", "10.0.1.2")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"email\":\"" + unverified.get("email") + "\"}")).andReturn().getResponse();
        assertEquals(200, r.getStatus());
        assertEquals(neutral[1], r.getContentAsString(StandardCharsets.UTF_8));
        awaitMail();
        assertNotEquals("verify:tok-body", jdbc.queryForObject("SELECT reset_token FROM users WHERE id=?", String.class, unverified.get("id")), "el token se renovo en segundo plano");
    }

    @Test void enlaceVencidoPideReenviar() {
        jdbc.update("UPDATE users SET reset_token=?, reset_token_expiration=? WHERE id=?", "verify:" + com.gesfut.services.impl.OneTimeTokens.hash("tok-vencido"), LocalDateTime.now().minusHours(1), unverified.get("id"));
        var ex = assertThrows(IllegalStateException.class, () -> auth.verifyEmail("tok-vencido"));
        assertTrue(ex.getMessage().contains("venci") && ex.getMessage().contains("Reenviar mail"));
    }

    @Test void reenviarInvalidaElEnlaceAnteriorYVenceEn24h() {
        jdbc.update("UPDATE users SET reset_token=?, reset_token_expiration=? WHERE id=?", "verify:" + com.gesfut.services.impl.OneTimeTokens.hash("tok-viejo"), LocalDateTime.now().plusHours(5), unverified.get("id"));
        auth.resendVerificationEmail((String) unverified.get("email"));
        var ex = assertThrows(IllegalArgumentException.class, () -> auth.verifyEmail("tok-viejo"));
        assertTrue(ex.getMessage().contains("Reenviar mail"));
        var exp = jdbc.queryForObject("SELECT reset_token_expiration FROM users WHERE id=?", java.sql.Timestamp.class, unverified.get("id")).toLocalDateTime();
        assertTrue(exp.isAfter(LocalDateTime.now().plusHours(23)) && exp.isBefore(LocalDateTime.now().plusHours(25)));
    }
}
