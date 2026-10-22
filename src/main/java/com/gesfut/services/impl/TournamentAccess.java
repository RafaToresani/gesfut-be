package com.gesfut.services.impl;

import com.gesfut.config.security.SecurityUtils;
import com.gesfut.models.tournament.Tournament;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class TournamentAccess {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    public void requireOwner(Tournament tournament) {
        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null || !email.equals(tournament.getUser().getEmail())) {
            throw new AccessDeniedException("No tenés permiso para modificar este torneo.");
        }
        if (entityManager != null && entityManager.contains(tournament)) entityManager.lock(tournament, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }
}
