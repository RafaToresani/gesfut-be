package com.gesfut;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gesfut.dtos.requests.MatchDateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 10/11: como llega al back la fecha que manda el front. El front resta 3 h y hace toISOString(),
 * o sea manda la hora de pared de Argentina con sufijo Z. Se verifica, con el mismo ObjectMapper por defecto
 * de Spring Boot (sin config propia de Jackson en el proyecto), que el LocalDateTime queda con esa hora de pared
 * y NO se convierte, sin importar la zona horaria del servidor. Sin Spring ni base.
 */
class MatchDateRequestJsonTests {
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    private LocalDateTime read(String json, String serverZone) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(serverZone));
            return mapper.readValue("{\"localDateTime\":\"" + json + "\"}", MatchDateRequest.class).localDateTime();
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test void conZSeGuardaLaHoraDePared() throws Exception {
        LocalDateTime esperado = LocalDateTime.of(2026, 11, 14, 20, 0);
        assertEquals(esperado, read("2026-11-14T20:00:00.000Z", "UTC"));
        assertEquals(esperado, read("2026-11-14T20:00:00.000Z", "America/Argentina/Buenos_Aires"));
        assertEquals(esperado, read("2026-11-14T20:00:00.000Z", "Asia/Tokyo"));
    }

    @Test void sinZSeGuardaIgual() throws Exception {
        assertEquals(LocalDateTime.of(2026, 11, 14, 20, 0), read("2026-11-14T20:00:00", "UTC"));
    }
}