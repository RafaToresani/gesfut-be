package com.gesfut.models.matchDay;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Plan 22/10 #9: el resultado se carga recien desde el dia del partido. */
class MatchResultDayTests {
    Match at(LocalDateTime d) { Match m = new Match(); m.setDate(d); return m; }

    @Test void manianaNoSeCarga() { assertFalse(at(LocalDate.now().plusDays(1).atTime(0, 1)).resultLoadableToday()); }
    @Test void hoyMasTardeSiSeCarga() { assertTrue(at(LocalDate.now().atTime(23, 59)).resultLoadableToday()); }
    @Test void ayerSiSeCarga() { assertTrue(at(LocalDateTime.now().minusDays(1)).resultLoadableToday()); }
    @Test void sinFechaSeCarga() { assertTrue(at(null).resultLoadableToday()); }
}
