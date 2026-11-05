package com.gesfut.config.security.jwt;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JwtService {
    @Value("${SECRET.KEY}")
    private String SECRET_KEY;

    /** A4 #1 (31/10): solo los JWT de sesion llevan typ=session; cualquier otro JWT (sin typ) se rechaza (falla cerrado). */
    public static final String TYP_CLAIM = "typ";
    public static final String TYP_SESSION = "session";
    /** Punto 4 (5/11): instante de emision en milisegundos. El iat estandar tiene resolucion de segundos y dejaba
     *  valida una sesion emitida en el mismo segundo pero antes del cambio de contrasena. */
    public static final String IAT_MS_CLAIM = "iat_ms";

    public String getToken(UserDetails user,  Collection<? extends GrantedAuthority> authorities) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
        claims.put(TYP_CLAIM, TYP_SESSION);
        return getToken(claims, user);
    }

    private String getToken(Map<String, Object> extraClaims, UserDetails user) {
        long now = System.currentTimeMillis();
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put(IAT_MS_CLAIM, now);
        return Jwts
                .builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(new Date(now))
                .expiration(new Date(now+1000*60*60*24))
                .signWith(getKey())
                .compact();
    }

    private SecretKey getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String getUsernameFromToken(String token) {
        return getClaim(token, Claims::getSubject);
    }

    private <T> T getClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token, UserDetails userDetails)
    {
        final String username = getUsernameFromToken(token);

        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token) && isSessionToken(token)
                && (!(userDetails instanceof com.gesfut.models.user.UserEntity u) || isIssuedAfterPasswordChange(token, u.getPasswordChangedAt())));
    }

    private Claims getAllClaims(String token){
        return Jwts
                .parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValidNotUser(String token) {
        try {
            Claims claims = getAllClaims(token);
            return TYP_SESSION.equals(claims.get(TYP_CLAIM)) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpirationMillis(String token){
        return getExpiration(token).getTime();
    }

    private Date getExpiration(String token){
        return getClaim(token, Claims::getExpiration);
    }

    private Boolean isTokenExpired(String token){
        return getExpiration(token).before(new Date());
    }

    /** A4 #2 + punto 4 (5/11): la sesion vale solo si se emitio despues del ultimo cambio de contrasena.
     *  Tokens nuevos: se compara iat_ms (milisegundos) contra passwordChangedAt redondeado hacia arriba al milisegundo,
     *  asi una sesion emitida en el mismo segundo pero antes del cambio queda invalidada y el login nuevo (posterior) entra.
     *  Tokens emitidos antes de este cambio (sin iat_ms): se mantiene la regla vieja por segundos. */
    public boolean isIssuedAfterPasswordChange(String token, java.time.LocalDateTime changedAt) { // changedAt en UTC
        if (changedAt == null) return true;
        try {
            Claims claims = getAllClaims(token);
            Object ms = claims.get(IAT_MS_CLAIM);
            if (ms instanceof Number n) return n.longValue() >= changedAtCeilMillis(changedAt);
            Date iat = claims.getIssuedAt();
            if (iat == null) return false;
            return iat.getTime() / 1000 >= changedAt.toEpochSecond(java.time.ZoneOffset.UTC); // passwordChangedAt se guarda en UTC
        } catch (RuntimeException e) { return false; }
    }

    /** passwordChangedAt (UTC) en milisegundos epoch, redondeado hacia arriba (un token del mismo ms pero antes no pasa). */
    static long changedAtCeilMillis(java.time.LocalDateTime changedAt) {
        java.time.Instant i = changedAt.toInstant(java.time.ZoneOffset.UTC);
        long ms = i.toEpochMilli();
        return i.getNano() % 1_000_000 == 0 ? ms : ms + 1;
    }

    /** iat_ms del token (milisegundos epoch, para tests y diagnostico); -1 si no lo tiene. */
    public long getIssuedAtEpochMillis(String token) {
        try { Object ms = getAllClaims(token).get(IAT_MS_CLAIM); return ms instanceof Number n ? n.longValue() : -1; } catch (RuntimeException e) { return -1; }
    }

    /** iat del token en segundos epoch (para tests y diagnostico); -1 si no se puede leer. */
    public long getIssuedAtEpochSeconds(String token) {
        try { return getClaim(token, Claims::getIssuedAt).getTime() / 1000; } catch (RuntimeException e) { return -1; }
    }

    /** A4 #1: true solo si es un JWT de sesion valido con typ=session; ante cualquier error, false. */
    public boolean isSessionToken(String token) {
        try { return TYP_SESSION.equals(getAllClaims(token).get(TYP_CLAIM)); }
        catch (RuntimeException e) { return false; }
    }
}
