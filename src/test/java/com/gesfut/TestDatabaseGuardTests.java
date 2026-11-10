package com.gesfut;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seguridad: los tests solo corren sobre la base aislada gesfut_isolated_test (la inyecta test-isolated.ps1 y la valida IsolatedDbGuard)
 *  (src/test/resources/config/application.properties). */
@SpringBootTest
class TestDatabaseGuardTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired JavaMailSender mailSender;

    @Test void siempreLaBaseAislada() {
        assertEquals("gesfut_isolated_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
    }

    /** Seguridad: en la corrida aislada el mail NUNCA apunta a un SMTP real. application.properties trae un host publico literal;
     *  test-isolated.ps1 lo pisa con SPRING_MAIL_HOST=127.0.0.1 y SPRING_MAIL_PORT=1 (puerto cerrado). Valor EFECTIVO del contexto. */
    @Test void elMailSoloVaALoopbackConPuertoCerrado() {
        assertNotNull(mailSender);
        JavaMailSenderImpl impl = (JavaMailSenderImpl) mailSender;
        assertTrue(Set.of("127.0.0.1", "localhost", "::1").contains(impl.getHost()), "spring.mail.host efectivo no es loopback");
        assertEquals(1, impl.getPort(), "spring.mail.port efectivo debe ser 1 (cerrado)");
    }
}
