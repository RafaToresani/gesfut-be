package com.gesfut;

import com.gesfut.config.security.dtos.LoginRequest;
import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.services.impl.AuthServiceImpl;
import com.gesfut.services.impl.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Deuda temporal (31/10): con gesfut.mail.verificacion.enabled=false (default) el registro queda verificado y no se manda mail. Solo base de prueba. */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate"
})
class MailVerificacionDesactivadaTests {
    @Autowired AuthServiceImpl auth;
    @Autowired JdbcTemplate jdbc;
    @MockBean EmailService emailService;

    @Test void registroQuedaVerificadoSinMailYPuedeLoguearse() {
        assertEquals("gesfut_isolated_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        String email = "qa.autoverify." + System.nanoTime() + "@example.test";
        String pass = "Qa-" + java.util.UUID.randomUUID();
        auth.singUp(new RegisterRequest(email, pass, "QaAuto", "Verify", true));
        Map<String, Object> row = jdbc.queryForMap("SELECT email_verified, reset_token FROM users WHERE email=?", email);
        assertEquals(true, ((Boolean) row.get("email_verified")) || Integer.valueOf(1).equals(row.get("email_verified")));
        assertNull(row.get("reset_token"));
        assertNotNull(auth.logIn(new LoginRequest(email, pass)).token());
        auth.resendVerificationEmail(email);
        auth.resendVerificationEmail("nadie-" + System.nanoTime() + "@example.test");
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @AfterEach void limpiar() {
        String like = "qa.autoverify.%@example.test";
        jdbc.update("DELETE FROM players WHERE team_id IN (SELECT t.id FROM teams t JOIN users u ON u.id = t.user_id WHERE u.email LIKE ?)", like);
        jdbc.update("DELETE FROM teams WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", like);
    }
}
