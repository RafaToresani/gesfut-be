package com.gesfut;

import com.gesfut.config.security.dtos.LoginRequest;
import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.config.security.jwt.JwtService;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.impl.AuthServiceImpl;
import com.gesfut.services.impl.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** A4 #3 (31/10): email y token en el body del POST; rutas viejas eliminadas sin alias; link del mail con #t=. Solo base de prueba. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/gesfut_codex_regression_20261013",
    "spring.jpa.hibernate.ddl-auto=validate",
    "gesfut.mail.verificacion.enabled=false"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ResetYChangePasswordEnBodyTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthServiceImpl auth;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @MockBean EmailService emailService;
    static final ObjectMapper JSON = new ObjectMapper();
    static final AtomicInteger IP_SEQ = new AtomicInteger();
    /** IPs propias de esta clase (10.251.x.x) para no compartir cupo con otros tests. */
    static String ip() { int n = IP_SEQ.incrementAndGet(); return "10.251." + (n / 250) + "." + (n % 250 + 1); }

    @AfterEach void limpiar() {
        String like = "qa.p3.%@example.test";
        jdbc.update("DELETE FROM players WHERE team_id IN (SELECT t.id FROM teams t JOIN users u ON u.id = t.user_id WHERE u.email LIKE ?)", like);
        jdbc.update("DELETE FROM teams WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", like);
    }

    String[] cuenta() {
        assertEquals("gesfut_codex_regression_20261013", jdbc.queryForObject("SELECT DATABASE()", String.class));
        String email = "qa.p3." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        String clave = "Qa-" + UUID.randomUUID();
        auth.singUp(new RegisterRequest(email, clave, "QaP3", "Uno", true));
        return new String[]{email, clave};
    }

    MockHttpServletResponse postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body).header("CF-Connecting-IP", ip())).andReturn().getResponse();
    }

    /** Pide el reset por la ruta nueva y devuelve el cuerpo del mail. */
    String pedirResetPorHttp(String email) throws Exception {
        clearInvocations(emailService);
        assertEquals(200, postJson("/api/v1/auth/reset-password", JSON.writeValueAsString(Map.of("email", email))).getStatus());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(5000)).sendEmail(eq(email), anyString(), body.capture());
        return body.getValue();
    }

    static String tokenDelLink(String mail) {
        String marker = "/auth/reset-password#t=";
        assertTrue(mail.contains(marker), "el link usa el fragmento #t=");
        return mail.substring(mail.indexOf(marker) + marker.length()).trim();
    }

    @Test void resetYChangePasswordPorBodyFuncionanYElTokenNoQuedaEnLaUrlNiEnLosLogs(CapturedOutput out) throws Exception {
        String[] c = cuenta();
        String mail = pedirResetPorHttp(c[0]);
        assertFalse(mail.contains("/auth/reset-password/"), "el token ya no va en el path del link");
        String tok = tokenDelLink(mail);
        assertTrue(tok.length() >= 40 && !tok.contains("/") && !tok.contains("?"), "token opaco base64url");
        String nueva = "Qa-" + UUID.randomUUID();
        var r = mvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("token", tok, "password", nueva))).header("CF-Connecting-IP", ip())).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        assertEquals("/api/v1/auth/change-password", r.getRequest().getRequestURI(), "la URL no lleva el token");
        assertNull(r.getRequest().getQueryString());
        assertNotNull(auth.logIn(new LoginRequest(c[0], nueva)).token(), "entra con la nueva");
        assertEquals(404, postJson("/api/v1/auth/change-password", JSON.writeValueAsString(Map.of("token", tok, "password", "Otra-" + UUID.randomUUID()))).getStatus(), "reusado: 404 (token invalido)");
        assertFalse(out.getAll().contains(tok), "el log no contiene el token");
        assertFalse(out.getAll().contains(c[0]), "el log no contiene el mail");
        assertFalse(out.getAll().contains(nueva), "el log no contiene la contrasena");
    }

    @Test void rutasViejasNoExistenMas() throws Exception {
        String[] c = cuenta();
        // Token real de reset: por la ruta vieja no debe servir.
        String tok = tokenDelLink(pedirResetPorHttp(c[0]));
        clearInvocations(emailService);
        // Anonimo: las rutas viejas ya no son publicas (deny-by-default) -> 401, nunca 200.
        assertEquals(401, mvc.perform(post("/api/v1/auth/reset-password/" + c[0]).header("CF-Connecting-IP", ip())).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(post("/api/v1/auth/change-password/" + tok).contentType(MediaType.TEXT_PLAIN).content("Vieja-" + UUID.randomUUID())
                .header("CF-Connecting-IP", ip())).andReturn().getResponse().getStatus());
        // Con sesion valida: no hay handler -> 404 (no alias).
        var u = users.findByEmail(c[0]).orElseThrow();
        String sesion = jwt.getToken(u, u.getAuthorities());
        assertEquals(404, mvc.perform(post("/api/v1/auth/reset-password/" + c[0]).header("Authorization", "Bearer " + sesion)).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(post("/api/v1/auth/change-password/" + tok).header("Authorization", "Bearer " + sesion)
                .contentType(MediaType.TEXT_PLAIN).content("Vieja-" + UUID.randomUUID())).andReturn().getResponse().getStatus());
        // Metodo equivocado sobre la ruta nueva (con sesion, para llegar al dispatcher): 405.
        assertEquals(405, mvc.perform(get("/api/v1/auth/reset-password").header("Authorization", "Bearer " + sesion)).andReturn().getResponse().getStatus());
        assertEquals(405, mvc.perform(get("/api/v1/auth/change-password").header("Authorization", "Bearer " + sesion)).andReturn().getResponse().getStatus());
        // Nada se proceso: ni mail nuevo ni token consumido.
        Thread.sleep(300);
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        assertTrue(jdbc.queryForObject("SELECT reset_token FROM users WHERE email=?", String.class, c[0]).startsWith("reset:"), "el token sigue sin usar");
    }

    @Test void bodyInvalidoDa400() throws Exception {
        assertEquals(400, postJson("/api/v1/auth/reset-password", "{}").getStatus(), "sin email");
        assertEquals(400, postJson("/api/v1/auth/reset-password", "{\"email\":\"   \"}").getStatus(), "email vacio");
        assertEquals(400, postJson("/api/v1/auth/reset-password", "{no-json").getStatus(), "json roto");
        assertEquals(400, mvc.perform(post("/api/v1/auth/reset-password").header("CF-Connecting-IP", ip())).andReturn().getResponse().getStatus() / 100 * 100, "sin body: 4xx");
        assertEquals(400, postJson("/api/v1/auth/change-password", "{\"password\":\"NuevaClave-123\"}").getStatus(), "sin token");
        assertEquals(400, postJson("/api/v1/auth/change-password", "{\"token\":\"abc\"}").getStatus(), "sin password");
        assertEquals(400, postJson("/api/v1/auth/change-password", "{no-json").getStatus(), "json roto");
        assertEquals(400, postJson("/api/v1/auth/change-password", "{\"token\":\"abc\",\"password\":\"corta\"}").getStatus(), "password corta");
        // Formato viejo (password como texto crudo) ya no se acepta.
        int crudo = mvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.TEXT_PLAIN).content("NuevaClave-123")
                .header("CF-Connecting-IP", ip())).andReturn().getResponse().getStatus();
        assertTrue(crudo == 400 || crudo == 415, "texto crudo rechazado: " + crudo);
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test void changePasswordOnceavoPedidoEnUnMinutoDesdeLaMismaIpDa429() throws Exception {
        String miIp = "10.253.0.1"; // reservada para este test
        String body = JSON.writeValueAsString(Map.of("token", "inexistente-" + UUID.randomUUID(), "password", "NuevaClave-123"));
        for (int i = 0; i < 10; i++) assertEquals(404, mvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content(body).header("CF-Connecting-IP", miIp)).andReturn().getResponse().getStatus(), "pedido " + (i + 1));
        var r11 = mvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content(body).header("CF-Connecting-IP", miIp)).andReturn().getResponse();
        assertEquals(429, r11.getStatus(), "11.o pedido en un minuto desde la misma IP");
        assertEquals("60", r11.getHeader("Retry-After"));
        assertEquals(404, mvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content(body).header("CF-Connecting-IP", "10.253.0.2")).andReturn().getResponse().getStatus(), "otra IP no se ve afectada");
    }

    @Test void losDtosNoImprimenDatosSensibles() {
        String tok = "tok-" + UUID.randomUUID(), pass = "Clave-" + UUID.randomUUID(), mail = "qa.p3.dto@example.test";
        String a = new com.gesfut.controllers.AuthController.ChangePasswordRequest(tok, pass).toString();
        String b = new com.gesfut.controllers.AuthController.ResetPasswordRequest(mail).toString();
        assertEquals("ChangePasswordRequest[***]", a);
        assertEquals("ResetPasswordRequest[***]", b);
        assertFalse(a.contains(tok) || a.contains(pass) || b.contains(mail));
    }

    @Test void rateLimitSigueAndandoConElMailEnElBody() throws Exception {
        String mail = "qa.p3.rl-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        for (int i = 0; i < 3; i++) assertEquals(200, postJson("/api/v1/auth/reset-password", JSON.writeValueAsString(Map.of("email", i == 1 ? " " + mail.toUpperCase() + " " : mail))).getStatus());
        var r4 = postJson("/api/v1/auth/reset-password", JSON.writeValueAsString(Map.of("email", mail)));
        assertEquals(429, r4.getStatus(), "4.o pedido al mismo mail (normalizado) desde otra IP");
        assertEquals("3600", r4.getHeader("Retry-After"));
        String miIp = "10.252.0.1"; // reservada para este test
        for (int i = 0; i < 3; i++) assertEquals(200, mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", "qa.p3.ip" + i + "-" + UUID.randomUUID() + "@example.test"))).header("CF-Connecting-IP", miIp)).andReturn().getResponse().getStatus());
        assertEquals(429, mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"qa.p3.otro@example.test\"}").header("CF-Connecting-IP", miIp)).andReturn().getResponse().getStatus(), "4.o desde la misma IP");
    }
}
