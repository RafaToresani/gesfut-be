package com.gesfut.services.impl;

import com.gesfut.config.security.SecurityUtils;
import com.gesfut.models.tournament.Tournament;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class TournamentAccess {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    /** Revision critica #2: un torneo finalizado es irreversible y no admite cambios (409). */
    public void requireOpen(Tournament tournament) {
        if (Boolean.TRUE.equals(tournament.getIsFinished()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "El torneo est\u00e1 finalizado: ya no admite cambios.");
    }

    public void requireOwner(Tournament tournament) {
        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null || !email.equals(tournament.getUser().getEmail())) {
            throw new AccessDeniedException("No tenés permiso para modificar este torneo.");
        }
        if (entityManager != null && entityManager.contains(tournament)) entityManager.lock(tournament, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }
}
