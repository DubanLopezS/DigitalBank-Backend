package com.fabricaescuela.digitalbank.transaccion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReversoRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 10, max = 255, message = "El motivo debe tener entre 10 y 255 caracteres")
        String motivo) {

    // Se recorta antes de validar: los espacios de los extremos no cuentan.
    public ReversoRequest {
        motivo = motivo == null ? null : motivo.trim();
    }
}
