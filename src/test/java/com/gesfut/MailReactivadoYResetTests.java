package com.gesfut;

import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.services.impl.AuthServiceImpl;
import com.gesfut.services.impl.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Lote 1 (31/10): mail de verificacion reactivado, reset con SMTP caido y changePassword. Sender mockeado; solo base de prueba. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate",
    "gesfut.mail.verificacion.enabled=true"
})
@AutoConfigureMockMvc
class MailReactivadoYResetTests {
    @Autowired AuthServiceImpl auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockBean EmailService emailService;

    String nuevaCuenta() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        String email = "qa.lote1." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        auth.singUp(new RegisterRequest(email, "Qa-" + UUID.randomUUID(), "QaLote", "Uno"));
        return email;
    }

    @Test void conMailActivadoElRegistroQuedaSinVerificarYSeIntentaEnviar() {
        String email = nuevaCuenta();
        var row = jdbc.queryForMap("SELECT email_verified+0 AS v, reset_token FROM users WHERE email=?", email);
        assertEquals(0, ((Number) row.get("v")).intValue());
        assertTrue(String.valueOf(row.get("reset_token")).startsWith("verify:"));
        verify(emailService, times(1)).sendEmail(eq(email), anyString(), contains("/auth/verify-email/"));
    }

    @Test void resetConSmtpCaidoResponde200NeutroIgualQueMailInexistente() throws Exception {
        String email = nuevaCuenta();
        clearInvocations(emailService); // el registro ya mando el mail de verificacion; se espera el de recuperacion (asincrono)
        doThrow(new MailAuthenticationException("auth failed")).when(emailService).sendEmail(anyString(), anyString(), anyString());
        var ok = mvc.perform(post("/api/v1/auth/reset-password/" + email).header("CF-Connecting-IP", "10.9.0.1")).andReturn().getResponse();
        var nadie = mvc.perform(post("/api/v1/auth/reset-password/nadie-" + UUID.randomUUID() + "@example.test").header("CF-Connecting-IP", "10.9.0.2")).andReturn().getResponse();
        assertEquals(200, ok.getStatus());
        assertEquals(200, nadie.getStatus());
        assertEquals(nadie.getContentAsString(StandardCharsets.UTF_8), ok.getContentAsString(StandardCharsets.UTF_8));
        verify(emailService, timeout(5000)).sendEmail(eq(email), anyString(), anyString());
        assertTrue(jdbc.queryForObject("SELECT reset_token FROM users WHERE email=?", String.class, email).startsWith("reset:"));
        assertDoesNotThrow(() -> auth.resetPasswordSendEmail(email));
    }

    @Test void changePasswordTokenVencidoYTokenReusado() {
        String email = nuevaCuenta();
        jdbc.update("UPDATE users SET reset_token=?, reset_token_expiration=? WHERE email=?", "reset:" + com.gesfut.services.impl.OneTimeTokens.hash("tok-vencido-" + email), LocalDateTime.now().minusMinutes(1), email);
        assertThrows(IllegalStateException.class, () -> auth.changePassword("tok-vencido-" + email, "NuevaClave-123"));
        jdbc.update("UPDATE users SET reset_token=?, reset_token_expiration=? WHERE email=?", "reset:" + com.gesfut.services.impl.OneTimeTokens.hash("tok-uso-" + email), LocalDateTime.now().plusMinutes(10), email);
        auth.changePassword("tok-uso-" + email, "NuevaClave-123");
        assertThrows(ResourceNotFoundException.class, () -> auth.changePassword("tok-uso-" + email, "OtraClave-456"));
    }
}