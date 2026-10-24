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
        return !tokenDenylist.isRevoked(token) && this.jwtService.isTokenValidNotUser(token);
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
    @PostMapping("/sing-up")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse singUp(@RequestBody @Valid RegisterRequest request, BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        return this.authService.singUp(request);
    }

    @Operation(
            summary = "Permite enviar al usuario un link para crear una nueva contraseña.",
            description = "El email debe exisitir."
    )
    @PostMapping("/reset-password/{email}")
    @ResponseStatus(HttpStatus.OK)
    public void resetPassword(@PathVariable String email) {
        this.authService.resetPasswordSendEmail(email);
    }

    @Operation(
            summary = "Permite cambiar y/o recuperar la contraseña del usuario.",
            description = "El token debe ser válido (el que se envia por email) y la contraseña nueva debe contener al menos 8 caracteres."
    )
    @PostMapping("/change-password/{token}")
    @ResponseStatus(HttpStatus.OK)
    public void changePassword(@PathVariable String token, @RequestBody String password) {
        this.authService.changePassword(token, password);
    }

    @Operation(
            summary = "Permite cambiar la contraseña del usuario.",
            description = "Permite crear una nueva contraseña con la antigua contraseña."
    )
    @PostMapping("/change-password-with-old-password")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void changePasswordWithOldPassword(
            @RequestBody PasswordsRequest passwords,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String token = com.gesfut.config.security.jwt.CookieAuth.anyToken(httpRequest);
       this.authService.changePasswordWithOldPassword(passwords.oldPassword(), passwords.newPassword(), token);
    }

    @PostMapping("/verify-email/{token}")
    public void verifyEmail(@PathVariable String token) {
        this.authService.verifyEmail(token);
    }

    static final String RESEND_NEUTRAL = "Si el mail existe y falta verificarlo, te lo mandamos. Revis\u00e1 tu casilla (y spam).";

    @PostMapping("/resend-verification/{email}")
    public ResponseEntity<java.util.Map<String, String>> resendVerification(@PathVariable String email) {
        // Siempre la misma respuesta: no revela si la cuenta existe o ya esta verificada.
        try { this.authService.resendVerificationEmail(email); } catch (RuntimeException ignored) { }
        return ResponseEntity.ok(java.util.Map.of("message", RESEND_NEUTRAL));
    }



}