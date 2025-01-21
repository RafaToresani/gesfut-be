package com.gesfut.dtos.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PrizesRequest(
        @NotEmpty(message = "La lista de premios no puede estar vacía")
        @Valid
        List<PrizeRequest> prizes,
        @NotBlank(message = "El código no puede estar vacío")
        String code
) {
}
