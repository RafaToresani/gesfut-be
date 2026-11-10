package com.gesfut.testsupport;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;

/**
 * Registra {@link IsolatedDbConnectionGuard} en TODOS los contextos que arma Spring Boot en los tests.
 *
 * <p>Por qué un ApplicationContextInitializer (vía spring.factories) y no AutoConfiguration.imports:
 * las auto-configuraciones solo se cargan con {@code @EnableAutoConfiguration} completo; los "slices"
 * ({@code @DataJpaTest}, {@code @JdbcTest}, etc.) usan su propia lista y lo saltearían.
 * SpringApplication aplica los initializers de spring.factories en cualquier contexto Boot (incluidos slices),
 * y {@code addBeanPostProcessor} lo deja activo antes de que se cree cualquier bean.
 */
public class IsolatedDbGuardInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getBeanFactory().addBeanPostProcessor(new IsolatedDbConnectionGuard(context.getEnvironment()));
    }
}
