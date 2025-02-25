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

    @Operation(
            summary = "Permite iniciar sesión al usuario.",
            description = "El email debe tener formato de email.")
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse logIn(@RequestBody @Valid LoginRequest request, BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        return this.authService.logIn(request);
    }

    @Operation(
            summary = "Valida si el token es válido.",
            description = "Recibe un token y verifica su validez."
    )
    @PostMapping("/validate-token/{token}")
    @ResponseStatus(HttpStatus.OK)
    public boolean validateToken(@PathVariable String token) {
        return this.jwtService.isTokenValidNotUser(token);
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
            @RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
       this.authService.changePasswordWithOldPassword(passwords.oldPassword(), passwords.newPassword(), token);
    }

    @PostMapping("/verify-email/{token}")
    public void verifyEmail(@PathVariable String token) {
        this.authService.verifyEmail(token);
    }

    @PostMapping("/resend-verification/{email}")
    public ResponseEntity<Void> resendVerification(@PathVariable String email) {
        this.authService.resendVerificationEmail(email);
        return ResponseEntity.ok().build();
    }



}