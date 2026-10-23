package com.gesfut.services.impl;

import com.gesfut.dtos.requests.PrizeRequest;
import com.gesfut.dtos.requests.PrizesRequest;
import com.gesfut.models.tournament.EPrizeType;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.repositories.PrizeRepository;
import com.gesfut.repositories.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Plan 22/10 #1: con el torneo terminado no se crean, editan ni borran premios (409). */
@ExtendWith(MockitoExtension.class)
class PrizeLockTests {
    @Mock TournamentAccess access;
    @Mock PrizeRepository prizes;
    @Mock TournamentRepository tournaments;
    @InjectMocks PrizeServiceImpl service;

    final UUID code = UUID.randomUUID();
    final PrizeRequest req = new PrizeRequest(EPrizeType.POSITION, "Trofeo", 1);

    Tournament t(boolean finished) {
        Tournament t = new Tournament(); t.setId(1L); t.setCode(code); t.setIsFinished(finished);
        when(tournaments.findByCode(code)).thenReturn(Optional.of(t));
        return t;
    }

    @Test void crearBloqueado() {
        t(true);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.createPrizes(new PrizesRequest(List.of(req), code.toString())));
        assertEquals(409, e.getStatusCode().value());
        verify(prizes, never()).save(any());
    }
    @Test void editarBloqueado() {
        t(true);
        assertThrows(ResponseStatusException.class, () -> service.partiallyUpdatePrize(code.toString(), req));
        verify(prizes, never()).save(any());
    }
    @Test void borrarBloqueado() {
        t(true);
        assertThrows(ResponseStatusException.class, () -> service.deletePrizeByCodeAndCategoryAndPosition(code.toString(), "POSITION", 1));
        verify(prizes, never()).deletePrizeById(any());
    }
    @Test void enCursoSePuedeCrear() {
        t(false);
        service.createPrizes(new PrizesRequest(List.of(req), code.toString()));
        verify(prizes).save(any());
    }
}
