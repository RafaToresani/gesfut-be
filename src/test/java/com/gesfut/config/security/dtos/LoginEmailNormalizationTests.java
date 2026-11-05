package com.gesfut.config.security.dtos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gesfut.config.security.jwt.JwtService;
import com.gesfut.controllers.AuthController;
import com.gesfut.services.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Tema 4 (5/11): login normaliza el email (trim + minusculas, como reset-password) antes de validar; la contrasena no se toca.
 *  Sin base ni contexto Spring: controller standalone con AuthService mockeado. Datos TEST. */
class LoginEmailNormalizationTests {
    AuthService auth;
    MockMvc mvc;
    final ObjectMapper om = new ObjectMapper();

    @BeforeEach void setUp() {
        AuthController c = new AuthController();
        auth = mock(AuthService.class);
        ReflectionTestUtils.setField(c, "authService", auth);
        ReflectionTestUtils.setField(c, "jwtService", mock(JwtService.class));
        LocalValidatorFactoryBean v = new LocalValidatorFactoryBean(); v.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(c).setValidator(v).setControllerAdvice(new com.gesfut.exceptions.GlobalExceptionHandler()).build();
        when(auth.logIn(any())).thenReturn(new AuthResponse("Qa", "Test", "tok-test", "ADMIN"));
    }

    int login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(om.writeValueAsString(Map.of("email", email, "password", password)))).andReturn().getResponse().getStatus();
    }

    @Test void emailConEspaciosYMayusculasEntraNormalizado() throws Exception {
        assertEquals(200, login("  QA.Login@Example.TEST  ", "clave-test-1"));
        ArgumentCaptor<LoginRequest> cap = ArgumentCaptor.forClass(LoginRequest.class);
        verify(auth).logIn(cap.capture());
        assertEquals("qa.login@example.test", cap.getValue().email());
        assertEquals("qa.login@example.test", new LoginRequest(" QA.Login@Example.TEST ", "x").email());
    }

    @Test void contrasenaConEspaciosNoSeRecorta() throws Exception {
        assertEquals(200, login(" qa@example.test ", "  clave con espacios  "));
        ArgumentCaptor<LoginRequest> cap = ArgumentCaptor.forClass(LoginRequest.class);
        verify(auth).logIn(cap.capture());
        assertEquals("  clave con espacios  ", cap.getValue().password());
    }

    @Test void emailMalformadoSigueDando400ConElMismoMensaje() throws Exception {
        var r = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(om.writeValueAsString(Map.of("email", "  no-es-mail  ", "password", "clave-test-1")))).andReturn();
        assertEquals(400, r.getResponse().getStatus());
        assertEquals("Formato de email inv\u00e1lido", r.getResolvedException().getMessage());
        assertTrue(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("Formato de email inv\u00e1lido"), "mismo mensaje en el body");
        verify(auth, never()).logIn(any());
    }
}