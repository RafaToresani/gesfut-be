package com.gesfut.controllers;

import com.gesfut.config.security.dtos.AuthResponse;
import com.gesfut.config.security.dtos.LoginRequest;
import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.config.security.jwt.JwtService;
import com.gesfut.dtos.requests.PasswordsRequest;
import com.gesfut.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.gesfut.config.security.jwt.TokenDenylist tokenDenylist;

    @Autowired
    private com.gesfut.repositories.UserRepository userRepository;

    @Operation(
            summary = "Permite iniciar sesión al usuario.",
            description = "El email debe tener formato de email.")
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse logIn(@RequestBody @Valid LoginRequest request, BindingResult bindingResult,
                              jakarta.servlet.http.HttpServletRequest httpRequest, jakarta.servlet.http.HttpServletResponse httpResponse) throws BadRequestException {
        if(bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        AuthResponse auth = this.authService.logIn(request);
        // Plan #13: el JWT va solo en la cookie httpOnly; el body ya no lo expone.
        com.gesfut.config.security.jwt.CookieAuth.issue(httpRequest, httpResponse, auth.token(), java.time.Duration.ofHours(24));
        return new AuthResponse(auth.name(), auth.lastName(), null, auth.role());
    }

    @Operation(
            summary = "Valida si el token es válido.",
            description = "Recibe un token y verifica su validez."
    )
    // Loop 4: el token viaja en el header Authorization, no en la URL (las URLs quedan en logs de proxy/tunel).
    // La ruta vieja /validate-token/{token} se elimino.
    @PostMapping("/validate-token")
    @ResponseStatus(HttpStatus.OK)
    public boolean validateToken(jakarta.servlet.http.HttpServletRequest httpRequest) {
        String token = com.gesfut.config.security.jwt.CookieAuth.anyToken(httpRequest);
        if (token == null) return false;
        if (tokenDenylist.isRevoked(token) || !this.jwtService.isTokenValidNotUser(token)) return false;
        // A4 #2: una sesion emitida antes del ultimo cambio de contrasena ya no es valida.
        try {
            var user = userRepository.findByEmail(jwtService.getUsernameFromToken(token));
            return user.isPresent() && jwtService.isIssuedAfterPasswordChange(token, user.get().getPasswordChangedAt());
        } catch (RuntimeException e) { return false; }
    }

    // Loop 5: logout del lado del servidor. El token queda revocado hasta su vencimiento.
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(jakarta.servlet.http.HttpServletRequest httpRequest, jakarta.servlet.http.HttpServletResponse httpResponse) {
        String token = com.gesfut.config.security.jwt.CookieAuth.anyToken(httpRequest);
        if (token != null) {
            try { tokenDenylist.revoke(token, jwtService.getExpirationMillis(token)); }
            catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) { /* vencido o invalido: solo se borra la cookie */ }
        }
        // Plan #13: borra la cookie de sesion y la de CSRF.
        com.gesfut.config.security.jwt.CookieAuth.clear(httpRequest, httpResponse);
    }

    @Operation(
            summary = "Permite iniciar sesión al usuario.",
            description =
                    "El email debe tener formato de email. " +
                    "La contraseña debe contener al menos 8 caracteres. " +
                    "El nombre y el apellido deben contener entre 2 y 50 caracteres."
    )
    // "/sing-up" queda como alias obsoleto (clientes viejos / PWA cacheada); el nuevo es "/signup".
    @PostMapping({"/signup", "/sing-up"})
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse singUp(@RequestBody @Valid RegisterRequest request, BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        return this.authService.singUp(request);
    }

    @Operation(
            summary = "Permite enviar al usuario un link para crear una nueva contraseña.",
            description = "El email debe exisitir."
    )
    // A4 #3 (31/10): el email viaja en el body (POST JSON {email}); la ruta vieja /reset-password/{email} se elimino, sin alias.
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    public void resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        // A4 #4: respuesta inmediata e identica exista o no el mail; la busqueda y el envio corren en otro hilo
        // (el tiempo de respuesta no revela si la cuenta existe). El log nunca lleva el mail ni el token.
        String target = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        mailExecutor.execute(() -> {
            try { this.authService.resetPasswordSendEmail(target); }
            catch (RuntimeException e) { log.error("Fallo la recuperacion de contrasena: {}", e.getClass().getSimpleName()); }
        });
    }

    @Operation(
            summary = "Permite cambiar y/o recuperar la contraseña del usuario.",
            description = "El token debe ser válido (el que se envia por email) y la contraseña nueva debe contener al menos 8 caracteres."
    )
    // A4 #3 (31/10): token y contrasena en el body (POST JSON {token, password}); la ruta vieja /change-password/{token} se elimino, sin alias.
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.OK)
    public void changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        this.authService.changePassword(request.token(), request.password());
    }

    public record ResetPasswordRequest(@jakarta.validation.constraints.NotBlank(message = "El email es obligatorio.") String email) { }

    public record ChangePasswordRequest(
            @jakarta.validation.constraints.NotBlank(message = "El token es obligatorio.") String token,
            @jakarta.validation.constraints.NotBlank(message = "La contrase\u00f1a es obligatoria.") String password) { }

    @Operation(
            summary = "Permite cambiar la contraseña del usuario.",
            description = "Permite crear una nueva contraseña con la antigua contraseña."
    )
    @PostMapping("/change-password-with-old-password")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void changePasswordWithOldPassword(
            @RequestBody PasswordsRequest passwords,
            jakarta.servlet.http.HttpServletRequest httpRequest, jakarta.servlet.http.HttpServletResponse httpResponse) {
        // A4 #1 fixup (M4): el usuario sale del SecurityContext, no se vuelve a leer el token.
        this.authService.changePasswordWithOldPassword(passwords.oldPassword(), passwords.newPassword());
        // A4 #2: el cambio cierra las sesiones anteriores; esta sesion recibe una cookie nueva para no quedar afuera.
        String email = com.gesfut.config.security.SecurityUtils.getCurrentUserEmail();
        userRepository.findByEmail(email).ifPresent(u -> com.gesfut.config.security.jwt.CookieAuth.issue(httpRequest, httpResponse,
                jwtService.getToken(u, u.getAuthorities()), java.time.Duration.ofHours(24)));
    }

    @PostMapping("/verify-email/{token}")
    public void verifyEmail(@PathVariable String token) {
        this.authService.verifyEmail(token);
    }

    static final String RESEND_NEUTRAL = "Si el mail existe y falta verificarlo, te lo mandamos. Revis\u00e1 tu casilla (y spam).";

    public record ResendRequest(@jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max = 254) String email) { }

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("mailExecutor")
    private org.springframework.core.task.TaskExecutor mailExecutor;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    /** Reenviar mail de verificacion: email en el body (no queda en logs de acceso ni historial). */
    @PostMapping("/resend-verification")
    public ResponseEntity<java.util.Map<String, String>> resendVerification(@RequestBody(required = false) @Valid ResendRequest request, BindingResult bindingResult) {
        // Email con formato invalido: misma respuesta neutral, sin encolar nada.
        if (bindingResult.hasErrors()) return queueResend(null);
        return queueResend(request == null ? null : request.email());
    }

    /** @deprecated alias viejo con el email en la ruta (apps cacheadas); usar POST /resend-verification con {email}. */
    @Deprecated
    @PostMapping("/resend-verification/{email}")
    public ResponseEntity<java.util.Map<String, String>> resendVerificationLegacy(@PathVariable String email) {
        return queueResend(email);
    }

    /** Siempre la misma respuesta y enseguida: la busqueda y el envio corren en otro hilo,
     *  asi ni el cuerpo ni el tiempo de respuesta revelan si la cuenta existe o ya esta verificada. */
    private ResponseEntity<java.util.Map<String, String>> queueResend(String email) {
        if (email != null && !email.isBlank()) {
            String target = email.trim().toLowerCase();
            // Si la cola esta llena, el RejectedExecutionHandler del pool lo descarta y lo loguea (no lanza).
            mailExecutor.execute(() -> {
                try { this.authService.resendVerificationEmail(target); }
                catch (RuntimeException e) { log.warn("Fallo el reenvio de verificacion: {}", e.getClass().getSimpleName()); }
            });
        }
        return ResponseEntity.ok(java.util.Map.of("message", RESEND_NEUTRAL));
    }



}