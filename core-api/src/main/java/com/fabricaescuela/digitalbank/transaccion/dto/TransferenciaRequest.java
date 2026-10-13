package com.fabricaescuela.digitalbank.transaccion.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferenciaRequest(
        @NotBlank(message = "La cuenta destino es obligatoria")
        String cuentaDestino,

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "Monto inválido")
        BigDecimal monto,

        @Size(max = 255, message = "La descripción no puede superar los 255 caracteres")
        String descripcion
) {}
