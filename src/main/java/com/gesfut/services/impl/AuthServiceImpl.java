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

    // TODO(Mateo): reactivar envio de mail de verificacion -> gesfut.mail.verificacion.enabled=true (deuda temporal hasta rotar la credencial de mail).
    // En false: la cuenta nueva queda verificada al registrarse y no se manda ningun mail (registro ni reenvio). Token, reenvio y limite 3/h quedan intactos.
    @org.springframework.beans.factory.annotation.Value("${gesfut.mail.verificacion.enabled:false}")
    boolean mailVerificacionEnabled;

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
            throw new IllegalStateException("Email no verificado. Por favor, verificá tu email antes de iniciar sesión.");
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

        String verificationToken = OneTimeTokens.newRaw();


        UserEntity user = UserEntity.builder()
                .name(request.name())
                .lastname(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(ERole.MANAGER)
                .emailVerified(!mailVerificacionEnabled)
                .resetToken(VERIFY_PREFIX + OneTimeTokens.hash(verificationToken))
                .resetTokenExpiration(LocalDateTime.now().plusHours(24))
                .build();


        if (!mailVerificacionEnabled) { user.setResetToken(null); user.setResetTokenExpiration(null); }
        user = this.userRepository.save(user);
        Set<PlayerRequest> players = new HashSet<>();
        players.add(new PlayerRequest("Free", "Free", true, true, 10));
        this.teamService.createDummyTeam(new TeamRequest("Free", "#FFF", players), user);

        if (mailVerificacionEnabled) {
        // Auditoria 22/10: si falla el SMTP el usuario ya quedo creado; no devolver 500 (puede pedir "Reenviar enlace").
        try {
        this.emailService.sendEmail(
                request.email(),
                "GESFUT - Verificación de email",
                "Para verificar tu email, haz click en el siguiente enlace: " +
                        frontBaseUrl() + "/auth/verify-email/" + verificationToken
        );
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el mail de verificacion: {}", e.getClass().getSimpleName());
        }
        }
        return new AuthResponse(
                user.getName(),
                user.getLastname(),
                // Loop 5: sin token hasta verificar el email (antes el registro devolvia un JWT valido y salteaba la verificacion).
                null,
                user.getRole().name());
    }

    public void verifyEmail(String token) {
        // A4 #1: uso atomico (un solo UPDATE); el prefijo verify: impide usar un token de reset.
        String key = VERIFY_PREFIX + OneTimeTokens.hash(token == null ? "" : token);
        if (token != null && !token.isBlank() && userRepository.consumeVerifyToken(key, LocalDateTime.now()) == 1) return;
        if (token != null && !token.isBlank() && userRepository.existsByResetToken(key)) {
            throw new IllegalStateException("El enlace de verificaci\u00f3n venci\u00f3. Ped\u00ed uno nuevo con \"Reenviar mail\" desde el inicio de sesi\u00f3n.");
        }
        throw new IllegalArgumentException("El enlace de verificaci\u00f3n no es v\u00e1lido o ya se us\u00f3. Ped\u00ed uno nuevo con \"Reenviar mail\" desde el inicio de sesi\u00f3n.");
    }

    public void resendVerificationEmail(String email) {
        // Loop 5: respuesta igual exista o no la cuenta, y este o no verificada (no enumerar emails).
        UserEntity user = userRepository.findByEmail(email).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getEmailVerified())) return;
        if (!mailVerificacionEnabled) return; // TODO(Mateo): ver gesfut.mail.verificacion.enabled; respuesta neutra sin enviar.

        String newToken = OneTimeTokens.newRaw();
        user.setResetToken(VERIFY_PREFIX + OneTimeTokens.hash(newToken));
        user.setResetTokenExpiration(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        // Nunca se loguea el token; si falla el SMTP la respuesta sigue siendo la misma (neutral).
        try {
        emailService.sendEmail(
                email,
                "GESFUT - Verificación de email",
                "Para verificar tu email, haz click en el siguiente enlace: " +
                        frontBaseUrl() + "/auth/verify-email/" + newToken
        );
        } catch (RuntimeException e) {
            log.warn("No se pudo reenviar el mail de verificacion: {}", e.getClass().getSimpleName());
        }
    }



    public void resetPasswordSendEmail(String email) {
        Optional<UserEntity> user = userRepository.findByEmail(email);
        // Loop 4: no revelar si el email existe; el front ya muestra "Si X tiene una cuenta, te enviamos...".
        if (user.isEmpty()) return;

        String token = OneTimeTokens.newRaw();

        user.get().setResetToken(RESET_PREFIX + OneTimeTokens.hash(token));
        user.get().setResetTokenExpiration(LocalDateTime.now().plusMinutes(30));
        userRepository.save(user.get());

        // Loop 6: si falla el envio se registra y se responde igual (200) para no revelar que la cuenta existe.
        try {
        this.emailService.sendEmail(email, "GESFUT - Recuperación de contraseña",
                "Hola, para recuperar tu contraseña, haz click en el siguiente enlace: " +
                        frontBaseUrl() + "/auth/reset-password#t=" + token);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el mail de recuperacion: {}", e.getClass().getSimpleName());
        }
    }


    // Loop 4: misma regla que el registro (min 8) y tope de BCrypt (72 bytes).
    private static void validateNewPassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8) throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new IllegalArgumentException("La contraseña es demasiado larga (máximo 72 caracteres).");
    }

    public void changePassword(String token, String newPassword) {
        validateNewPassword(newPassword);
        // A4 #1: uso atomico (un solo UPDATE que devuelve filas afectadas); dos usos simultaneos: gana uno solo.
        String key = RESET_PREFIX + OneTimeTokens.hash(token == null ? "" : token);
        String hashedPassword = passwordEncoder.encode(newPassword);
        if (token != null && !token.isBlank() && userRepository.consumeResetToken(key, hashedPassword, LocalDateTime.now(), LocalDateTime.now(java.time.ZoneOffset.UTC)) == 1) return;
        if (token != null && !token.isBlank() && userRepository.existsByResetToken(key)) throw new IllegalStateException("El token ha expirado.");
        throw new ResourceNotFoundException("El token es inv\u00e1lido.");
    }

    @Override
    public void changePasswordWithOldPassword(String oldPassword, String newPassword) {
        validateNewPassword(newPassword);
        // A4 #1 fixup (M3/M4): el usuario sale del SecurityContext (lo puso el filtro solo con un JWT typ=session).
        // Sin sesion valida: 401, no 404.
        String email = com.gesfut.config.security.SecurityUtils.getCurrentUserEmail();
        if (email == null) throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Sin sesion");
        Optional<UserEntity> user = userRepository.findByEmail(email);
        if (user.isEmpty()) throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Sin sesion");
        if (!passwordEncoder.matches(oldPassword, user.get().getPassword())) {
            throw new IllegalStateException("La contraseña antigua no coincide.");
        }
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.get().setPassword(hashedPassword);
        user.get().setPasswordChangedAt(LocalDateTime.now(java.time.ZoneOffset.UTC)); // A4 #2: cierra las sesiones anteriores (en UTC)
        userRepository.save(user.get());
    }

}
