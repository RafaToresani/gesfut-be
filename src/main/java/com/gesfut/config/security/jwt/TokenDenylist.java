package com.gesfut.config.security.jwt;

import com.gesfut.models.user.RevokedToken;
import com.gesfut.repositories.RevokedTokenRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loop 5: tokens revocados por logout. Se guarda el SHA-256 del token (nunca el token) hasta su vencimiento.
 * Plan 22/10 #14: persistido en MySQL (tabla revoked_tokens) para sobrevivir a un reinicio; la memoria es cache.
 * Limpieza: cada hora se borran los vencidos.
 */
@Component
public class TokenDenylist {
    private static final Logger log = LoggerFactory.getLogger(TokenDenylist.class);
    private final Map<String, Long> revoked = new ConcurrentHashMap<>();
    private RevokedTokenRepository repository;

    public TokenDenylist() {}

    @Autowired(required = false)
    public TokenDenylist(RevokedTokenRepository repository) { this.repository = repository; }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @PostConstruct
    void load() {
        if (repository == null) return;
        long now = System.currentTimeMillis();
        repository.findAllByExpiresAtGreaterThan(now).forEach(r -> revoked.put(r.getTokenHash(), r.getExpiresAt()));
        log.info("Denylist: {} tokens revocados vigentes cargados", revoked.size());
    }

    public void revoke(String token, long expiresAtMillis) {
        purge();
        String h = hash(token);
        revoked.put(h, expiresAtMillis);
        if (repository != null) repository.save(new RevokedToken(h, expiresAtMillis));
    }

    public boolean isRevoked(String token) {
        if (token == null) return false;
        String h = hash(token);
        Long exp = revoked.get(h);
        if (exp == null && repository != null) {
            // Otra instancia o un reinicio: la base es la fuente de verdad.
            exp = repository.findById(h).map(RevokedToken::getExpiresAt).orElse(null);
            if (exp != null) revoked.put(h, exp);
        }
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) { revoked.remove(h); return false; }
        return true;
    }

    void purge() {
        long now = System.currentTimeMillis();
        revoked.entrySet().removeIf(e -> e.getValue() < now);
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void cleanup() {
        purge();
        if (repository != null) {
            int n = repository.deleteExpired(System.currentTimeMillis());
            if (n > 0) log.info("Denylist: {} tokens vencidos borrados", n);
        }
    }

    int size() { return revoked.size(); }
}
