package com.gesfut.services.impl;

import com.gesfut.config.security.dtos.AuthResponse;
import com.gesfut.config.security.dtos.LoginRequest;
import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.config.security.jwt.JwtService;
import com.gesfut.dtos.requests.PlayerRequest;
import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.exceptions.ResourceAlreadyExistsException;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.user.ERole;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.AuthService;
import com.gesfut.services.TeamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthServiceImpl implements AuthService {
    // Loop 5: cada token sirve solo para su proposito (un link de verificacion no puede cambiar la contrasena) y es de un solo uso.
    static final String VERIFY_PREFIX = "verify:";
    static final String RESET_PREFIX = "reset:";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamService teamService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private EmailService emailService;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthServiceImpl.class);

    /** Auditoria 22/10: base de los links de mail. app.frontend.url si esta definida; si no, el primer origen https de FE_URL (o el primero). */
    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:}")
    private String frontendUrl;
    @org.springframework.beans.factory.annotation.Value("${cors.allowed.origins:http://localhost:4200}")
    private String allowedOrigins;

    String frontBaseUrl() {
        String base = frontendUrl;
        if (base == null || base.isBlank()) {
            String[] origins = allowedOrigins.split(",");
            base = origins[0].trim();
            for (String o : origins) if (o.trim().startsWith("https://")) { base = o.trim(); break; }
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Override
    public AuthResponse logIn(LoginRequest request) {

        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        Optional<UserEntity> user = userRepository.findByEmail(request.email());
        if(user.isEmpty()) throw new ResourceNotFoundException("El email ingresado no es válido.");
        if (!user.get().getEmailVerified()) {
            throw new IllegalStateException("Email no verificado. Por favor, verifica tu email antes de iniciar sesión.");
        }
        String token = jwtService.getToken(user.get(), user.get().getAuthorities());
        return new AuthResponse(user.get().getName(), user.get().getLastname(), token, user.get().getRole().name());
    }


    @Override
    public AuthResponse singUp(RegisterRequest request) {
        if (request.name().contains(" ")) throw new ResourceAlreadyExistsException("El nombre no puede contener espacios.");
        if (request.lastName().contains(" ")) throw new ResourceAlreadyExistsException("El apellido no puede contener espacios.");
        Optional<UserEntity> opt = this.userRepository.findByEmail(request.email());
        if(opt.isPresent()) throw new ResourceAlreadyExistsException("El email ingresado ya existe.");

        String verificationToken = jwtService.generateToken(request.email());


        UserEntity user = UserEntity.builder()
                .name(request.name())
                .lastname(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(ERole.MANAGER)
                .emailVerified(false)
                .resetToken(VERIFY_PREFIX + verificationToken)
                .resetTokenExpiration(LocalDateTime.now().plusHours(24))
                .build();


        user = this.userRepository.save(user);
        Set<PlayerRequest> players = new HashSet<>();
        players.add(new PlayerRequest("Free", "Free", true, true, 10));
        this.teamService.createDummyTeam(new TeamRequest("Free", "#FFF", players), user);

        // Auditoria 22/10: si falla el SMTP el usuario ya quedo creado; no devolver 500 (puede pedir "Reenviar enlace").
        try {
        this.emailService.sendEmail(
                request.email(),
                "GESFUT - Verificación de email",
                "Para verificar tu email, haz click en el siguiente enlace: " +
                        frontBaseUrl() + "/auth/verify-email/" + verificationToken
        );
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el mail de verificacion: {}", e.getMessage());
        }
        return new AuthResponse(
                user.getName(),
                user.getLastname(),
                // Loop 5: sin token hasta verificar el email (antes el registro devolvia un JWT valido y salteaba la verificacion).
                null,
                user.getRole().name());
    }

    public void verifyEmail(String token) {
        UserEntity user = userRepository.findByResetToken(VERIFY_PREFIX + token)
                .orElseThrow(() -> new ResourceNotFoundException("Token inválido"));

        if (user.getResetTokenExpiration().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("El token ha expirado");
        }

        user.setEmailVerified(true);
        user.setResetToken(null);
        user.setResetTokenExpiration(null);
        userRepository.save(user);
    }

    public void resendVerificationEmail(String email) {
        // Loop 5: respuesta igual exista o no la cuenta, y este o no verificada (no enumerar emails).
        UserEntity user = userRepository.findByEmail(email).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getEmailVerified())) return;

        String newToken = jwtService.generateToken(email);
        user.setResetToken(VERIFY_PREFIX + newToken);
        user.setResetTokenExpiration(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        emailService.sendEmail(
                email,
                "GESFUT - Verificación de email",
                "Para verificar tu email, haz click en el siguiente enlace: " +
                        frontBaseUrl() + "/auth/verify-email/" + newToken
        );
    }



    public void resetPasswordSendEmail(String email) {
        Optional<UserEntity> user = userRepository.findByEmail(email);
        // Loop 4: no revelar si el email existe; el front ya muestra "Si X tiene una cuenta, te enviamos...".
        if (user.isEmpty()) return;

        String token = jwtService.generateToken(email);

        user.get().setResetToken(RESET_PREFIX + token);
        user.get().setResetTokenExpiration(LocalDateTime.now().plusMinutes(30));
        userRepository.save(user.get());

        this.emailService.sendEmail(email, "GESFUT - Recuperación de contraseña",
                "Hola, para recuperar tu contraseña, haz click en el siguiente enlace: " +
                        frontBaseUrl() + "/auth/reset-password/" + token);
    }


    // Loop 4: misma regla que el registro (min 8) y tope de BCrypt (72 bytes).
    private static void validateNewPassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8) throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new IllegalArgumentException("La contraseña es demasiado larga (máximo 72 caracteres).");
    }

    public void changePassword(String token, String newPassword) {
        validateNewPassword(newPassword);
        Optional<UserEntity> user = userRepository.findByResetToken(RESET_PREFIX + token);
        if (user.isEmpty()) throw new ResourceNotFoundException("El token es inválido.");
        if (user.get().getResetTokenExpiration().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("El token ha expirado.");
        }
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.get().setPassword(hashedPassword);
        user.get().setResetToken(null);
        user.get().setResetTokenExpiration(null);
        userRepository.save(user.get());
    }

    @Override
    public void changePasswordWithOldPassword(String oldPassword, String newPassword, String token) {
        validateNewPassword(newPassword);
        Optional<UserEntity> user = userRepository.findByEmail(jwtService.getUsernameFromToken(token));
        if (user.isEmpty()) throw new ResourceNotFoundException("El token es inválido.");
        if (!passwordEncoder.matches(oldPassword, user.get().getPassword())) {
            throw new IllegalStateException("La contraseña antigua no coincide.");
        }
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.get().setPassword(hashedPassword);
        userRepository.save(user.get());
    }

}
