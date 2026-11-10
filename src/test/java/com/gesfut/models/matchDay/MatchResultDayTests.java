package com.gesfut.models.matchDay;

import com.gesfut.config.TimeConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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
}