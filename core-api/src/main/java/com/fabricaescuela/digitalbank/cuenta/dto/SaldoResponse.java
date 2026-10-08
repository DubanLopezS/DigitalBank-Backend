package com.fabricaescuela.digitalbank.cuenta.dto;

import java.math.BigDecimal;

public record SaldoResponse(
        String numeroCuenta,
        BigDecimal saldoContable,
        BigDecimal saldoDisponible
) {
}
