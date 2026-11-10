package com.gesfut.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj único de la aplicación. Los horarios de partido (Match.date, LocalDateTime) se guardan
 * como hora de pared de Argentina, así que "ahora" también se resuelve en esa zona.
 * Inyectar este Clock (en vez de llamar a LocalDateTime.now()) permite fijar la hora en los tests.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Bean
    public Clock clock() {
        return Clock.system(ARGENTINA);
    }
}
