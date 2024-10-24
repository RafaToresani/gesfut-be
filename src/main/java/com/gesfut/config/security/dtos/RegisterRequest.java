package com.gesfut.config.security.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(description = "El email del usuario", example = "joaopedro@gmail.com")
        @Email(message = "Formato de correo electrónico inválido")
        @NotBlank(message = "El email es obligatorio")
        String email,

        @Schema(description = "La contraseña del usuario", example = "12341234")
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password,

        @Schema(description = "El nombre del usuario", example = "Joao Pedro Jesus")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
        String name,

        @Schema(description = "El apellido del usuario", example = "de Nazaret Pereira Perez Da Silva")
        @NotBlank(message = "El apellido es obligatorio")
        @Size(min = 2, max = 50, message = "El apellido debe tener entre 2 y 50 caracteres")
        String lastName
) {
}
