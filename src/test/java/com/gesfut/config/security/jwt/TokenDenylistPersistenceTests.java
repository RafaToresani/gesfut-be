package com.gesfut.config.security.jwt;

import com.gesfut.models.user.RevokedToken;
import com.gesfut.repositories.RevokedTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Plan 22/10 #14: la denylist se guarda en la base y sobrevive a un reinicio. */
class TokenDenylistPersistenceTests {
    @Test
    void revokePersisteElHashNuncaElToken() {
        RevokedTokenRepository repo = mock(RevokedTokenRepository.class);
        TokenDenylist d = new TokenDenylist(repo);
        d.revoke("a.b.c", System.currentTimeMillis() + 60_000);
        ArgumentCaptor<RevokedToken> c = ArgumentCaptor.forClass(RevokedToken.class);
        verify(repo).save(c.capture());
        assertEquals(TokenDenylist.hash("a.b.c"), c.getValue().getTokenHash());
        assertNotEquals("a.b.c", c.getValue().getTokenHash());
    }

    @Test
    void trasReinicioElTokenSigueRevocado() {
        long exp = System.currentTimeMillis() + 60_000;
        RevokedTokenRepository repo = mock(RevokedTokenRepository.class);
        when(repo.findAllByExpiresAtGreaterThan(anyLong())).thenReturn(List.of(new RevokedToken(TokenDenylist.hash("t1"), exp)));
        when(repo.findById(TokenDenylist.hash("t2"))).thenReturn(Optional.of(new RevokedToken(TokenDenylist.hash("t2"), exp)));
        when(repo.findById(TokenDenylist.hash("libre"))).thenReturn(Optional.empty());
        TokenDenylist reiniciada = new TokenDenylist(repo);
        reiniciada.load();
        assertTrue(reiniciada.isRevoked("t1"));
        assertTrue(reiniciada.isRevoked("t2"));
        assertFalse(reiniciada.isRevoked("libre"));
    }

    @Test
    void cleanupBorraVencidosEnLaBase() {
        RevokedTokenRepository repo = mock(RevokedTokenRepository.class);
        when(repo.deleteExpired(anyLong())).thenReturn(3);
        new TokenDenylist(repo).cleanup();
        verify(repo).deleteExpired(anyLong());
    }
}
