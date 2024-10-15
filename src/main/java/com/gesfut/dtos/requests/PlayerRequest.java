package com.gesfut.dtos.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlayerRequest(

        @NotBlank(message = "Nombre es requerido")
        @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
        String name,

        @NotBlank(message = "Apellido es requerido")
        @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
        String lastName,

        @NotBlank(message = "Capitan es requerido")
        Boolean isCaptain,

        @NotBlank(message = "Portero es requerido")
        Boolean isGoalKeeper,

        @NotBlank(message = "Dorsal es requerido")
        @Min(value = 0, message = "El número debe ser mayor a 0")
        @Max(value = 99, message = "El número debe ser menor a 100")
        Integer number



    )
{

}
