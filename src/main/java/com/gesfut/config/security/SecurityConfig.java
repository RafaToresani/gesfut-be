package com.gesfut.config.security;

import com.gesfut.config.security.jwt.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.EnableGlobalAuthentication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {
    @Autowired
    private AuthenticationProvider authProvider;
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Autowired
    private AuthRateLimitFilter authRateLimitFilter;
    @Value("${cors.allowed.origins}")
    private String url;

    @Value("${app.security.swagger-public:false}")
    private boolean swaggerPublic;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // Auditoria 22/10: denegar por defecto. Solo es publico auth, la lectura publica de torneos y el preflight CORS.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Swagger publico solo si app.security.swagger-public=true (desarrollo, application-local.properties). Default: cerrado.
                        .requestMatchers(swaggerPublic ? new String[]{"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"} : new String[]{"/__swagger-cerrado__"}).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/auth/signup", "/api/v1/auth/sing-up", "/api/v1/auth/validate-token",
                                "/api/v1/auth/reset-password/*", "/api/v1/auth/change-password/*",
                                "/api/v1/auth/verify-email/*", "/api/v1/auth/resend-verification", "/api/v1/auth/resend-verification/*").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/v1/tournaments/*", "/api/v1/tournaments/short/*",
                                "/api/v1/tournaments/*/matches/*", "/api/v1/tournaments/*/top-scorers",
                                "/api/v1/tournaments/*/top-yellow-cards", "/api/v1/tournaments/*/top-red-cards",
                                "/api/v1/match-days/*", "/api/v1/match-days/*/lastPlayed",
                                "/api/v1/prizes/*", "/api/v1/prizes/*/*",
                                // Revision critica #14: solo lo que cuelga de un codigo de torneo; nada por id numerico.
                                "/api/v1/team-participant/*/teams").permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authProvider)
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        // Plan #13: credenciales (cookie) solo para los origenes listados en FE_URL.
                        .allowedOrigins(java.util.Arrays.stream(url.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new))
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                        .allowedHeaders("Content-Type", "Authorization", "X-XSRF-TOKEN")
                        .allowCredentials(true);
            }
        };
    }
}
