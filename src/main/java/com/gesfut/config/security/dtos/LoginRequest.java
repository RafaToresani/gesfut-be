package com.gesfut.config.security.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(description = "El email del usuario", example = "joaopedro@gmail.com")
        @Email(message = "Formato de email inválido")
        @NotBlank(message = "El email es obligatorio")
        String email,

        @Schema(description = "La contraseña del usuario", example = "12341234")
        @NotBlank(message = "La contraseña es obligatoria")
        String password
) {
}
