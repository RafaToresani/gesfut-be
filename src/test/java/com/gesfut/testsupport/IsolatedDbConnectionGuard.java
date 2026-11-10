package com.gesfut.testsupport;

import java.lang.reflect.Method;
import java.util.Map;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.AbstractDriverBasedDataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Segundo control, al momento de conectar: BeanPostProcessor de TEST.
 *
 * <p>Valida cada bean {@link DataSource} y el bean de Flyway con la URL/usuario CONFIGURADOS,
 * antes de que nadie pida una conexión (no se abre ninguna conexión para validar).
 * Corre en dos momentos:
 * <ul>
 *   <li>{@code postProcessBeforeInitialization}: si la URL ya está seteada, se valida.</li>
 *   <li>{@code postProcessAfterInitialization}: validación completa y obligatoria, ya con el binding
 *       de {@code spring.datasource.hikari.*} aplicado (ese binding también ocurre en un BPP).</li>
 * </ul>
 * HikariDataSource creado con el constructor sin argumentos (como lo hace Spring Boot) no abre el pool
 * hasta el primer getConnection(), así que frenar acá garantiza que no haya intento de conexión.
 *
 * <p>Cubre lo que el primer control no ve: {@code @DynamicPropertySource}, DataSources declarados como
 * {@code @Bean} en tests (DataSourceBuilder, DriverManagerDataSource, etc.) y el DataSource propio de Flyway.
 * Un tipo de DataSource que no se puede inspeccionar se RECHAZA (falla cerrado).
 *
 * <p>No cubre: conexiones abiertas a mano con DriverManager dentro de un test (eso lo detecta grep-checks.ps1).
 *
 * <p>Registro: {@link IsolatedDbGuardInitializer} (spring.factories), que lo agrega directo al BeanFactory.
 */
public final class IsolatedDbConnectionGuard implements BeanPostProcessor, Ordered {

    private static final String FLYWAY_CLASS = "org.flywaydb.core.Flyway";
    private static final int MAX_DEPTH = 5;

    private final Environment environment;

    public IsolatedDbConnectionGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        check(bean, beanName, false);
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        check(bean, beanName, true);
        return bean;
    }

    private void check(Object bean, String beanName, boolean complete) {
        if (bean instanceof DataSource ds) {
            validateDataSource(ds, "DataSource '" + beanName + "'", complete, 0);
        }
        if (isFlyway(bean)) {
            validateFlyway(bean, "Flyway '" + beanName + "'", complete);
        }
    }

    // ------------------------------------------------------------------------------------------

    void validateDataSource(DataSource ds, String what, boolean complete, int depth) {
        if (depth > MAX_DEPTH) {
            throw IsolatedDbRules.violation(what + " tiene demasiados envoltorios para inspeccionarlo");
        }
        if (ds instanceof HikariDataSource hikari) {
            validateHikari(hikari, what, complete);
            return;
        }
        if (ds instanceof DelegatingDataSource delegating) {
            DataSource target = delegating.getTargetDataSource();
            if (target == null) {
                if (complete) {
                    throw IsolatedDbRules.violation(what + " envuelve un DataSource nulo");
                }
                return;
            }
            validateDataSource(target, what + " -> destino", complete, depth + 1);
            return;
        }
        if (ds instanceof AbstractDriverBasedDataSource driverBased) {
            // Cubre DriverManagerDataSource y SimpleDriverDataSource.
            validateTargetIfPresent(driverBased.getUrl(), driverBased.getUsername(), what, complete);
            return;
        }
        // Tipo desconocido: se intenta por reflexión (por ejemplo, el DriverDataSource interno de Flyway).
        String url = invokeString(ds, "getJdbcUrl", "getUrl");
        String user = invokeString(ds, "getUsername", "getUser");
        if (url == null) {
            if (complete) {
                throw IsolatedDbRules.violation(what + " es de un tipo no reconocido ("
                        + ds.getClass().getName() + "); no se puede verificar su destino");
            }
            return;
        }
        validateTargetIfPresent(url, user, what, true);
    }

    private void validateHikari(HikariDataSource hikari, String what, boolean complete) {
        if (hikari.getDataSourceClassName() != null) {
            throw IsolatedDbRules.violation(what + " usa dataSourceClassName (ignora la URL); no permitido");
        }
        if (hikari.getDataSource() != null) {
            throw IsolatedDbRules.violation(what + " envuelve otro DataSource; no permitido");
        }
        if (hikari.getDataSourceJNDI() != null) {
            throw IsolatedDbRules.violation(what + " usa JNDI; no permitido");
        }
        IsolatedDbRules.validateDriverClassName(hikari.getDriverClassName(), what + " (driver)");
        Map<Object, Object> props = hikari.getDataSourceProperties();
        IsolatedDbRules.validateDriverProperties(props, what + " (dataSourceProperties)");
        validateTargetIfPresent(hikari.getJdbcUrl(), hikari.getUsername(), what, complete);
    }

    private void validateTargetIfPresent(String url, String user, String what, boolean complete) {
        if (url == null && !complete) {
            return; // se valida completo en postProcessAfterInitialization
        }
        IsolatedDbRules.validateTarget(url, user, expectedPort(), what);
    }

    private String expectedPort() {
        return environment == null ? null : environment.getProperty(IsolatedDbRules.EXPECTED_PORT_PROPERTY);
    }

    // ------------------------------------------------------------------------------------------
    // Flyway (por reflexión para no atarse a la API exacta de la versión de Flyway)
    // ------------------------------------------------------------------------------------------

    static boolean isFlyway(Object bean) {
        for (Class<?> c = bean.getClass(); c != null; c = c.getSuperclass()) {
            if (FLYWAY_CLASS.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    private void validateFlyway(Object flyway, String what, boolean complete) {
        Object configuration = invoke(flyway, "getConfiguration");
        if (configuration == null) {
            throw IsolatedDbRules.violation(what + " sin configuración inspeccionable");
        }
        Object cleanDisabled = invoke(configuration, "isCleanDisabled");
        if (!(cleanDisabled instanceof Boolean b) || !b) {
            throw IsolatedDbRules.violation(what + " no tiene cleanDisabled=true");
        }
        String url = invokeString(configuration, "getUrl");
        String user = invokeString(configuration, "getUser");
        boolean checkedSomething = false;
        if (url != null) {
            IsolatedDbRules.validateTarget(url, user, expectedPort(), what);
            checkedSomething = true;
        }
        Object ds = invoke(configuration, "getDataSource");
        if (ds instanceof DataSource dataSource) {
            validateDataSource(dataSource, what + " -> DataSource", true, 0);
            checkedSomething = true;
        }
        if (!checkedSomething && complete) {
            throw IsolatedDbRules.violation(what + " no tiene un destino verificable");
        }
    }

    // ------------------------------------------------------------------------------------------

    private static String invokeString(Object target, String... methodNames) {
        for (String name : methodNames) {
            Object v = invoke(target, name);
            if (v instanceof String s) {
                return s;
            }
        }
        return null;
    }

    /** Invoca un getter público sin argumentos. Si no existe devuelve null; si falla, rechaza (falla cerrado). */
    private static Object invoke(Object target, String methodName) {
        Method m;
        try {
            m = target.getClass().getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
        try {
            m.setAccessible(true);
            return m.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw IsolatedDbRules.violation("no se pudo inspeccionar " + target.getClass().getName()
                    + "." + methodName + "() (" + e.getClass().getSimpleName() + ")");
        }
    }
}
