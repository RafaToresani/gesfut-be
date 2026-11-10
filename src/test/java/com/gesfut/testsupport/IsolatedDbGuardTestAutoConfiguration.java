package com.gesfut.testsupport;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * RESPALDO de TEST para registrar {@link IsolatedDbConnectionGuard}. El registro principal sigue siendo
 * {@link IsolatedDbGuardInitializer} vía META-INF/spring.factories (corre en todo contexto Boot, incluidos slices).
 *
 * <p>Se lista en META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports (solo en src/test).
 * Al estar listada ahí, el AutoConfigurationExcludeFilter de {@code @SpringBootApplication} la excluye del
 * component scan aunque viva bajo com.gesfut.
 *
 * <p>Idempotente: si el initializer ya agregó un IsolatedDbConnectionGuard al BeanFactory, devuelve un
 * BeanPostProcessor que no hace nada (no se valida dos veces). El método es {@code static} para que el BPP
 * se cree sin instanciar esta configuración.
 */
@AutoConfiguration
public class IsolatedDbGuardTestAutoConfiguration {

    @Bean
    static BeanPostProcessor isolatedDbConnectionGuardBackup(ConfigurableListableBeanFactory beanFactory,
            Environment environment) {
        return backupFor(beanFactory, environment);
    }

    /** Lógica separada para testearla sin contexto. */
    static BeanPostProcessor backupFor(ConfigurableListableBeanFactory beanFactory, Environment environment) {
        if (isGuardAlreadyRegistered(beanFactory)) {
            return new AlreadyRegistered();
        }
        return new IsolatedDbConnectionGuard(environment);
    }

    static boolean isGuardAlreadyRegistered(ConfigurableListableBeanFactory beanFactory) {
        if (beanFactory instanceof AbstractBeanFactory abf) {
            for (BeanPostProcessor bpp : abf.getBeanPostProcessors()) {
                if (bpp instanceof IsolatedDbConnectionGuard) {
                    return true;
                }
            }
        }
        return false;
    }

    /** BPP vacío: el guardián ya está activo por el initializer. */
    static final class AlreadyRegistered implements BeanPostProcessor {
    }
}
