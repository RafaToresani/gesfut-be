package com.gesfut.models.matchDay;

import com.gesfut.config.TimeConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** 10/11: el resultado se carga recien desde la hora programada del partido (hora de Argentina). */
class MatchResultDayTests {
    Match at(LocalDateTime d) { Match m = new Match(); m.setDate(d); return m; }
    LocalDateTime now() { return LocalDateTime.now(TimeConfig.ARGENTINA); }

    @Test void manianaNoSeCarga() { assertFalse(at(now().plusDays(1)).resultLoadableToday()); }
    @Test void hoyMasTardeNoSeCarga() { assertFalse(at(now().plusMinutes(10)).resultLoadableToday()); }
    @Test void hoyYaPasadaLaHoraSiSeCarga() { assertTrue(at(now().minusMinutes(10)).resultLoadableToday()); }
    @Test void ayerSiSeCarga() { assertTrue(at(now().minusDays(1)).resultLoadableToday()); }
    @Test void sinFechaSeCarga() { assertTrue(at(null).resultLoadableToday()); }

    // Borde exacto con reloj fijo (el mismo Clock que inyecta MatchServiceImpl).
    static final LocalDateTime START = LocalDateTime.of(2026, 11, 14, 20, 0); // hora de pared ART
    Clock clockAt(Instant i) { return Clock.fixed(i, TimeConfig.ARGENTINA); }
    Instant startInstant() { return START.atZone(TimeConfig.ARGENTINA).toInstant(); }

    @Test void justoALaHoraSeCarga() { assertTrue(at(START).resultLoadableAt(clockAt(startInstant()))); }
    @Test void unSegundoAntesNoSeCarga() { assertFalse(at(START).resultLoadableAt(clockAt(startInstant().minusSeconds(1)))); }
    @Test void unSegundoDespuesSeCarga() { assertTrue(at(START).resultLoadableAt(clockAt(startInstant().plusSeconds(1)))); }
    @Test void elBordeNoDependeDeLaZonaDelReloj() {
        // mismo instante con reloj en UTC y en Tokio: la decision es la misma
        assertTrue(at(START).resultLoadableAt(Clock.fixed(startInstant(), ZoneId.of("UTC"))));
        assertFalse(at(START).resultLoadableAt(Clock.fixed(startInstant().minusSeconds(1), ZoneId.of("Asia/Tokyo"))));
    }
    @Test void sinFechaSeCargaConRelojFijo() { assertTrue(at(null).resultLoadableAt(clockAt(startInstant()))); }
}