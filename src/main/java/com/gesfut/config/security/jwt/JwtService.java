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

    public String getToken(UserDetails user,  Collection<? extends GrantedAuthority> authorities) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
        claims.put(TYP_CLAIM, TYP_SESSION);
        return getToken(claims, user);
    }

    private String getToken(Map<String, Object> extraClaims, UserDetails user) {
        return Jwts
                .builder()
                .claims(extraClaims)
                .subject(user.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis()+1000*60*60*24))
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

    /** A4 #2: la sesion vale si se emitio en el mismo segundo o despues del ultimo cambio de contrasena.
     *  El iat del JWT tiene resolucion de segundos: se comparan segundos (borde: un token emitido en el mismo
     *  segundo pero antes del cambio sigue valiendo; ventana menor a 1 s, aceptada para no romper el login nuevo). */
    public boolean isIssuedAfterPasswordChange(String token, java.time.LocalDateTime changedAt) {
        if (changedAt == null) return true;
        try {
            Date iat = getClaim(token, Claims::getIssuedAt);
            if (iat == null) return false;
            return iat.getTime() / 1000 >= changedAt.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        } catch (RuntimeException e) { return false; }
    }

    /** A4 #1: true solo si es un JWT de sesion valido con typ=session; ante cualquier error, false. */
    public boolean isSessionToken(String token) {
        try { return TYP_SESSION.equals(getAllClaims(token).get(TYP_CLAIM)); }
        catch (RuntimeException e) { return false; }
    }
}
