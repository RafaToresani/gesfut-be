package com.gesfut.config.security.dtos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gesfut.config.security.jwt.JwtService;
import com.gesfut.controllers.AuthController;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.AuthService;
import com.gesfut.services.TeamService;
import com.gesfut.services.impl.AuthServiceImpl;
import com.gesfut.services.impl.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Tema 7 (5/11): el registro exige aceptar Terminos y Privacidad y guarda acceptedTermsAt en UTC. Sin base: mocks. Datos TEST. */
class RegisterAcceptedTermsTests {
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
        when(auth.singUp(any())).thenReturn(new AuthResponse("QaNombre", "QaApellido", null, "MANAGER"));
    }

    MvcResult signup(Object acceptedTerms, boolean incluir) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("email", "qa.terms@example.test", "password", "clave-test-1", "name", "QaNombre", "lastName", "QaApellido"));
        if (incluir) body.put("acceptedTerms", acceptedTerms);
        return mvc.perform(post("/api/v1/auth/signup").contentType("application/json").content(om.writeValueAsString(body))).andReturn();
    }

    void assert400ConMensaje(MvcResult r) throws Exception {
        assertEquals(400, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString(StandardCharsets.UTF_8).contains(RegisterRequest.TERMS_MESSAGE), r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        verify(auth, never()).singUp(any());
    }

    @Test void sinAcceptedTermsDa400ConElMensaje() throws Exception { assert400ConMensaje(signup(null, false)); }
    @Test void acceptedTermsNullDa400ConElMensaje() throws Exception { assert400ConMensaje(signup(null, true)); }
    @Test void acceptedTermsFalseDa400ConElMensaje() throws Exception { assert400ConMensaje(signup(false, true)); }

    @Test void acceptedTermsTrueRegistra() throws Exception {
        MvcResult r = signup(true, true);
        assertEquals(201, r.getResponse().getStatus(), "el signup responde 201 Created");
        ArgumentCaptor<RegisterRequest> cap = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(auth).singUp(cap.capture());
        assertEquals(Boolean.TRUE, cap.getValue().acceptedTerms());
    }

    @Test void elServicioGuardaAcceptedTermsAtEnUtcReciente() {
        AuthServiceImpl svc = new AuthServiceImpl();
        UserRepository repo = mock(UserRepository.class);
        PasswordEncoder enc = mock(PasswordEncoder.class);
        when(repo.findByEmail(any())).thenReturn(Optional.empty());
        when(repo.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(enc.encode(any())).thenReturn("hash-test");
        ReflectionTestUtils.setField(svc, "userRepository", repo);
        ReflectionTestUtils.setField(svc, "passwordEncoder", enc);
        ReflectionTestUtils.setField(svc, "teamService", mock(TeamService.class));
        ReflectionTestUtils.setField(svc, "jwtService", mock(JwtService.class));
        ReflectionTestUtils.setField(svc, "emailService", mock(EmailService.class));
        LocalDateTime antes = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        try { svc.singUp(new RegisterRequest("qa.terms@example.test", "clave-test-1", "QaNombre", "QaApellido", true)); }
        catch (RuntimeException ignored) { /* lo que pase despues de guardar (token/respuesta) no es parte de este test */ }
        ArgumentCaptor<UserEntity> cap = ArgumentCaptor.forClass(UserEntity.class);
        verify(repo, atLeastOnce()).save(cap.capture());
        LocalDateTime at = cap.getAllValues().get(0).getAcceptedTermsAt();
        assertNotNull(at, "acceptedTermsAt guardado");
        assertTrue(!at.isBefore(antes) && at.isBefore(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)), "reciente y en UTC: " + at);
    }
}
