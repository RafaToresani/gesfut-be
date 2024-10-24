package com.gesfut.controllers;

import com.gesfut.config.security.dtos.AuthResponse;
import com.gesfut.config.security.dtos.LoginRequest;
import com.gesfut.config.security.dtos.RegisterRequest;
import com.gesfut.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

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

}