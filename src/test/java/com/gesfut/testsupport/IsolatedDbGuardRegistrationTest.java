package com.gesfut.testsupport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.FailureHandler;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.StringUtils;

/**
 * Comprueba que los guardianes quedan REGISTRADOS (no solo que existen las clases), sin levantar contexto.
 */
class IsolatedDbGuardRegistrationTest {

    private final ClassLoader classLoader = getClass().getClassLoader();

    /**
     * Otros EnvironmentPostProcessor/initializers de Spring Boot piden argumentos de constructor que acá no
     * damos; se ignoran sus fallas de instanciación (los nuestros tienen constructor sin argumentos).
     */
    private static final FailureHandler IGNORE_OTHERS = FailureHandler.handleMessage((message, failure) -> {
    });

    /** Nombres declarados para una clave en TODOS los META-INF/spring.factories (sin instanciar nada). */
    private List<String> declaredNames(String key) throws IOException {
        List<String> names = new ArrayList<>();
        for (URL url : Collections.list(classLoader.getResources(SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION))) {
            Properties props = new Properties();
            try (InputStream in = url.openStream()) {
                props.load(in);
            }
            String value = props.getProperty(key);
            if (value != null) {
                for (String n : StringUtils.commaDelimitedListToStringArray(value)) {
                    names.add(n.trim());
                }
            }
        }
        return names;
    }

    @Test
    void elGuardianDeEntornoEstaEnSpringFactories() throws IOException {
        assertTrue(declaredNames(EnvironmentPostProcessor.class.getName()).contains(IsolatedDbGuard.class.getName()));

        List<EnvironmentPostProcessor> loaded = SpringFactoriesLoader.forDefaultResourceLocation(classLoader)
                .load(EnvironmentPostProcessor.class, null, IGNORE_OTHERS);
        assertTrue(loaded.stream().anyMatch(IsolatedDbGuard.class::isInstance),
                "SpringFactoriesLoader no instanció IsolatedDbGuard");
    }

    @Test
    void elInitializerEstaEnSpringFactories() throws IOException {
        assertTrue(declaredNames(ApplicationContextInitializer.class.getName())
                .contains(IsolatedDbGuardInitializer.class.getName()));

        @SuppressWarnings("rawtypes")
        List<ApplicationContextInitializer> loaded = SpringFactoriesLoader.forDefaultResourceLocation(classLoader)
                .load(ApplicationContextInitializer.class, null, IGNORE_OTHERS);
        assertTrue(loaded.stream().anyMatch(IsolatedDbGuardInitializer.class::isInstance),
                "SpringFactoriesLoader no instanció IsolatedDbGuardInitializer");
    }

    @Test
    void laAutoConfiguracionDeRespaldoEstaEnElImports() {
        List<String> candidates = new ArrayList<>();
        ImportCandidates.load(AutoConfiguration.class, classLoader).forEach(candidates::add);
        assertTrue(candidates.contains(IsolatedDbGuardTestAutoConfiguration.class.getName()));
    }

    @Test
    void elRespaldoRegistraElGuardianSiNoEstaba() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        BeanPostProcessor bpp = IsolatedDbGuardTestAutoConfiguration.backupFor(bf, new MockEnvironment());
        assertInstanceOf(IsolatedDbConnectionGuard.class, bpp);
    }

    @Test
    void elRespaldoEsIdempotenteSiElInitializerYaLoRegistro() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        bf.addBeanPostProcessor(new IsolatedDbConnectionGuard(new MockEnvironment()));
        BeanPostProcessor bpp = IsolatedDbGuardTestAutoConfiguration.backupFor(bf, new MockEnvironment());
        assertFalse(bpp instanceof IsolatedDbConnectionGuard, "no debe registrar un segundo guardián");
        assertInstanceOf(IsolatedDbGuardTestAutoConfiguration.AlreadyRegistered.class, bpp);
    }
}
