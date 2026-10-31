package com.gesfut;

import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.services.impl.AuthServiceImpl;
import com.gesfut.services.impl.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** A4 #4 (31/10): recuperar contrasena con limite por mail e IP, respuesta identica y envio asincrono. Solo base de prueba. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate",
    "gesfut.mail.verificacion.enabled=false"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ResetRateLimitTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthServiceImpl auth;
    @MockBean EmailService emailService;

    String cuenta() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        String email = "qa.rl." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        auth.singUp(new RegisterRequest(email, "Qa-" + UUID.randomUUID(), "QaRl", "Uno"));
        return email;
    }
    static String ip() { return "10.7." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250); }

    MockHttpServletResponse reset(String email, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/reset-password/{e}", email).header("CF-Connecting-IP", ip)).andReturn().getResponse();
    }

    @Test void cuartoPedidoAlMismoMailDesdeOtraIpDa429IgualExistaONo() throws Exception {
        String existe = cuenta();
        String nadie = "nadie-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        String[] variantes = {existe, " " + existe.toUpperCase() + " ", existe};
        for (int i = 0; i < 3; i++) assertEquals(200, reset(variantes[i], ip()).getStatus(), "pedido " + (i + 1));
        for (int i = 0; i < 3; i++) assertEquals(200, reset(nadie, ip()).getStatus());
        var r4a = reset(existe.toUpperCase(), ip());
        var r4b = reset(nadie, ip());
        assertEquals(429, r4a.getStatus(), "4.o pedido al mismo mail (normalizado) desde otra IP");
        assertEquals(429, r4b.getStatus());
        assertEquals(r4b.getContentAsString(StandardCharsets.UTF_8), r4a.getContentAsString(StandardCharsets.UTF_8), "429 identico");
        assertEquals(r4b.getHeader("Retry-After"), r4a.getHeader("Retry-After"));
        assertEquals("3600", r4a.getHeader("Retry-After"));
    }

    @Test void mismaRespuestaYTiempoComparableConEnvioAsincrono(CapturedOutput out) throws Exception {
        String existe = cuenta();
        doAnswer(inv -> { Thread.sleep(1500); throw new MailAuthenticationException("auth failed"); })
                .when(emailService).sendEmail(anyString(), anyString(), anyString());
        String nadie = "nadie-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        reset("calentar-" + UUID.randomUUID() + "@example.test", ip());
        long t0 = System.nanoTime(); var a = reset(existe, ip()); long ta = (System.nanoTime() - t0) / 1_000_000;
        long t1 = System.nanoTime(); var b = reset(nadie, ip()); long tb = (System.nanoTime() - t1) / 1_000_000;
        assertEquals(200, a.getStatus());
        assertEquals(a.getStatus(), b.getStatus());
        assertEquals(b.getContentAsString(StandardCharsets.UTF_8), a.getContentAsString(StandardCharsets.UTF_8));
        assertTrue(ta < 1000, "el existente no espera al envio (" + ta + " ms)");
        assertTrue(tb < 1000, "el inexistente responde rapido (" + tb + " ms)");
        verify(emailService, timeout(5000)).sendEmail(eq(existe), anyString(), anyString());
        for (int i = 0; i < 100 && !out.getAll().contains("No se pudo enviar el mail de recuperacion"); i++) Thread.sleep(50);
        assertTrue(out.getAll().contains("No se pudo enviar el mail de recuperacion"));
        assertFalse(out.getAll().contains(existe), "el log no contiene el mail");
        assertFalse(out.getAll().contains(nadie), "el log no contiene el mail inexistente");
        String stored = jdbc.queryForObject("SELECT reset_token FROM users WHERE email=?", String.class, existe);
        assertFalse(out.getAll().contains(stored.substring("reset:".length())), "el log no contiene el hash del token");
    }
}
