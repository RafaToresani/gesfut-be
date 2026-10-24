package com.gesfut.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Envio de mails fuera del hilo del request: el tiempo de respuesta no revela si la cuenta existe.
 *  Pool chico y cola acotada; si se llena se descarta (y se loguea) en vez de bloquear o crecer sin limite. */
@Configuration
@EnableAsync
public class AsyncConfig {
    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "mailExecutor")
    public ThreadPoolTaskExecutor mailExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(1);
        e.setMaxPoolSize(2);
        e.setQueueCapacity(200);
        e.setThreadNamePrefix("mail-");
        e.setRejectedExecutionHandler((r, ex) -> log.warn("Cola de mails llena: se descarta un envio."));
        e.initialize();
        return e;
    }
}
