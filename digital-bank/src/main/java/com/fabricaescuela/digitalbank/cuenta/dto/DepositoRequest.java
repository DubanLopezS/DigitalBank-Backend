package com.fabricaescuela.digitalbank.cuenta.dto;

import com.fabricaescuela.digitalbank.cuenta.entity.OrigenDeposito;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositoRequest(
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "Monto inválido")
        BigDecimal monto,

        @NotNull(message = "El origen del depósito es obligatorio")
        OrigenDeposito origen
) {}
