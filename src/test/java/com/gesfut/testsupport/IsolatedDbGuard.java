package com.gesfut.testsupport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * Primer control: guardián de entorno de TEST (vive solo en src/test).
 *
 * <p>Corre con {@link Ordered#LOWEST_PRECEDENCE}, o sea DESPUÉS de ConfigDataEnvironmentPostProcessor,
 * cuando ya se cargaron application.properties, config/, imports, perfiles, variables de entorno,
 * argumentos y las properties inline de {@code @SpringBootTest(properties = ...)}.
 * Si el destino efectivo no es el contenedor descartable, aborta el arranque del contexto con
 * {@code IllegalStateException("GESFUT ISOLATION GUARD: ...")} ANTES de que exista cualquier DataSource.
 *
 * <p>Las lecturas usan {@link Binder}, que aplica el mismo relaxed binding que Spring Boot
 * (por ejemplo SPRING_DATASOURCE_HIKARI_JDBCURL == spring.datasource.hikari.jdbc-url).
 *
 * <p>Limitación: {@code @DynamicPropertySource} se agrega después de este punto; eso lo frena el
 * segundo control ({@link IsolatedDbConnectionGuard}).
 *
 * <p>Registro: src/test/resources/META-INF/spring.factories.
 */
public class IsolatedDbGuard implements EnvironmentPostProcessor, Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        validate(environment);
    }

    /** Punto de entrada reutilizable (y testeable con MockEnvironment). */
    public static void validate(Environment env) {
        try {
            doValidate(env);
        } catch (IllegalStateException e) {
            if (IsolatedDbRules.isGuardViolation(e)) {
                throw e;
            }
            throw IsolatedDbRules.violation("no se pudo resolver la configuración de datasource/flyway ("
                    + e.getClass().getSimpleName() + ")");
        } catch (RuntimeException e) {
            // No se propaga el mensaje original: podría contener valores de configuración.
            throw IsolatedDbRules.violation("no se pudo resolver la configuración de datasource/flyway ("
                    + e.getClass().getSimpleName() + ")");
        }
    }

    private static void doValidate(Environment env) {
        Binder binder = Binder.get(env);

        // ---- Perfiles: solo vacío o "test" ----
        List<String> profiles = new ArrayList<>(Arrays.asList(env.getActiveProfiles()));
        IsolatedDbRules.validateProfiles(profiles, "perfiles activos");
        IsolatedDbRules.validateProfiles(bindList(binder, "spring.profiles.active"), "spring.profiles.active");
        IsolatedDbRules.validateProfiles(bindList(binder, "spring.profiles.include"), "spring.profiles.include");

        // ---- Puerto verificado por el script (chequeo adicional, obligatorio) ----
        // Ojo: no es un ConfigurationPropertyName válido (mayúsculas), por eso va con getProperty y no con Binder.
        String expectedPort = env.getProperty(IsolatedDbRules.EXPECTED_PORT_PROPERTY);

        // ---- DataSource principal ----
        IsolatedDbRules.validateTarget(
                bindString(binder, "spring.datasource.url"),
                bindString(binder, "spring.datasource.username"),
                expectedPort,
                "spring.datasource");
        IsolatedDbRules.validateDriverClassName(bindString(binder, "spring.datasource.driver-class-name"),
                "spring.datasource.driver-class-name");
        if (bindString(binder, "spring.datasource.jndi-name") != null) {
            throw IsolatedDbRules.violation("spring.datasource.jndi-name no está permitido");
        }
        String type = bindString(binder, "spring.datasource.type");
        if (type != null && !IsolatedDbRules.HIKARI_TYPE.equals(type)) {
            throw IsolatedDbRules.violation("spring.datasource.type distinto de Hikari no está permitido");
        }
        if (bindString(binder, "spring.datasource.embedded-database-connection") != null) {
            throw IsolatedDbRules.violation("spring.datasource.embedded-database-connection no está permitido");
        }

        // ---- Overrides propios de Hikari (se aplican después de la URL principal) ----
        String hikariUrl = bindString(binder, "spring.datasource.hikari.jdbc-url");
        if (hikariUrl != null) {
            IsolatedDbRules.ValidatedUrl v = IsolatedDbRules.validateJdbcUrl(hikariUrl, "spring.datasource.hikari.jdbc-url");
            IsolatedDbRules.validateExpectedPort(expectedPort, v.port());
        }
        String hikariUser = bindString(binder, "spring.datasource.hikari.username");
        if (hikariUser != null) {
            IsolatedDbRules.validateUsername(hikariUser, "spring.datasource.hikari.username");
        }
        if (bindString(binder, "spring.datasource.hikari.data-source-class-name") != null) {
            throw IsolatedDbRules.violation("spring.datasource.hikari.data-source-class-name no está permitido");
        }
        // HikariConfig.setDataSourceJNDI: la forma kebab es "data-source-jndi". Spring Boot publica en su metadata
        // "data-source-j-n-d-i"; para el Binder ambas son el mismo nombre (compara sin guiones), pero se
        // consultan las dos para no depender de ese detalle.
        if (bindString(binder, "spring.datasource.hikari.data-source-jndi") != null
                || bindString(binder, "spring.datasource.hikari.data-source-j-n-d-i") != null) {
            throw IsolatedDbRules.violation("spring.datasource.hikari.data-source-jndi no está permitido");
        }
        IsolatedDbRules.validateDriverClassName(bindString(binder, "spring.datasource.hikari.driver-class-name"),
                "spring.datasource.hikari.driver-class-name");
        Map<String, String> dsProps = binder
                .bind("spring.datasource.hikari.data-source-properties", Bindable.mapOf(String.class, String.class))
                .orElse(null);
        IsolatedDbRules.validateDriverProperties(dsProps, "spring.datasource.hikari.data-source-properties");

        // ---- Flyway ----
        String flywayUrl = bindString(binder, "spring.flyway.url");
        if (flywayUrl != null) {
            IsolatedDbRules.ValidatedUrl v = IsolatedDbRules.validateJdbcUrl(flywayUrl, "spring.flyway.url");
            IsolatedDbRules.validateExpectedPort(expectedPort, v.port());
        }
        String flywayUser = bindString(binder, "spring.flyway.user");
        if (flywayUser != null) {
            IsolatedDbRules.validateUsername(flywayUser, "spring.flyway.user");
        }
        IsolatedDbRules.validateDriverClassName(bindString(binder, "spring.flyway.driver-class-name"),
                "spring.flyway.driver-class-name");
        IsolatedDbRules.validateCleanDisabled(bindString(binder, "spring.flyway.clean-disabled"));
        String cleanOnError = bindString(binder, "spring.flyway.clean-on-validation-error");
        if (cleanOnError != null && "true".equalsIgnoreCase(cleanOnError.trim())) {
            throw IsolatedDbRules.violation("spring.flyway.clean-on-validation-error=true no está permitido");
        }

        // ---- Otros caminos de conexión que no usamos ----
        if (bindString(binder, "spring.r2dbc.url") != null) {
            throw IsolatedDbRules.violation("spring.r2dbc.url no está permitido en los tests aislados");
        }
    }

    private static String bindString(Binder binder, String name) {
        return binder.bind(name, Bindable.of(String.class)).orElse(null);
    }

    private static List<String> bindList(Binder binder, String name) {
        return binder.bind(name, Bindable.listOf(String.class)).orElse(List.of());
    }
}
