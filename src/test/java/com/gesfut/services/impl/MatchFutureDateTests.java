package com.gesfut.services.impl;

import com.gesfut.dtos.requests.MatchDateRequest;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.repositories.MatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 10/11: un partido se puede programar tambien en fecha/hora pasada (carga historica). */
@ExtendWith(MockitoExtension.class)
class MatchFutureDateTests {
    @Mock TournamentAccess access;
    @Mock MatchRepository matches;
    @InjectMocks MatchServiceImpl service;

    Match match() {
        Tournament t = new Tournament(); t.setMatchDays(new HashSet<>());
        MatchDay d = new MatchDay(); d.setTournament(t); d.setNumberOfMatchDay(0);
        Match m = new Match(); m.setId(7L); m.setMatchDay(d);
        when(matches.findForUpdate(7L)).thenReturn(Optional.of(m));
        return m;
    }

    @Test void fechaPasadaAceptada() {
        Match m = match();
        LocalDateTime ayer = LocalDateTime.now().minusDays(1);
        service.updateMatchDateAndDescription(7L, new MatchDateRequest(ayer));
        assertEquals(ayer, m.getDate());
        verify(matches).save(m);
    }

    @Test void fechaFuturaAceptada() {
        match();
        service.updateMatchDateAndDescription(7L, new MatchDateRequest(LocalDateTime.now().plusDays(2)));
        verify(matches).save(any());
    }
}