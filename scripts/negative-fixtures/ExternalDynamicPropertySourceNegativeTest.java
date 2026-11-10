package com.gesfut.testsupport.negative;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * FIXTURE NEGATIVO. Vive en scripts/negative-fixtures (fuera de src/test: Maven no lo compila ni lo corre):
 * negative-runs.ps1 lo copia temporalmente a src/test/java/com/gesfut/testsupport/negative/ y lo borra al terminar.
 *
 * Simula un test externo que, con @DynamicPropertySource, intenta apuntar al MySQL80 nativo (3306).
 * El EnvironmentPostProcessor no ve estas propiedades (se agregan después); el BeanPostProcessor
 * IsolatedDbConnectionGuard tiene que frenarlo con "GESFUT ISOLATION GUARD" ANTES de que Hikari
 * arranque el pool. Se corre SIN contraseña: aunque el guardián fallara, no hay credencial válida.
 */
@SpringBootTest
class ExternalDynamicPropertySourceNegativeTest {

    @DynamicPropertySource
    static void apuntarAlMysqlNativo(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:3306/gesfut_isolated_test");
    }

    @Test
    void elContextoNoDebeLevantar() {
        fail("El contexto levantó: el guardián de conexión NO frenó @DynamicPropertySource");
    }
}
