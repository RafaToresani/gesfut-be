package com.gesfut.config.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private TokenDenylist tokenDenylist;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // Plan #13: primero la cookie httpOnly; si viene por cookie y el metodo escribe, exige CSRF (doble envio).
        String token = CookieAuth.sessionToken(request);
        if (token != null && !CookieAuth.csrfOk(request)) token = null;
        if (token == null) token = getTokenFromRequest(request);
        String email;

        // Loop 5: un token revocado por logout se trata como anonimo.
        if(token==null || tokenDenylist.isRevoked(token)){
            filterChain.doFilter(request, response);
            return;
        }
        try {
            email = jwtService.getUsernameFromToken(token);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            // Token vencido o inválido: se continúa como anónimo.
            // Los endpoints públicos responden normal y los protegidos devuelven 401/403.
            filterChain.doFilter(request, response);
            return;
        }

        if(email != null && SecurityContextHolder.getContext().getAuthentication() == null){
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if(jwtService.isTokenValid(token, userDetails)){
                UsernamePasswordAuthenticationToken  authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION);

        if(StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")){
            return authHeader.substring(7);
        }
        return null;
    }


}
