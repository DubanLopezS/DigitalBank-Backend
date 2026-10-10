package com.fabricaescuela.digitalbank.cuenta.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MovimientoSaldoResponse(
        UUID cuentaId,
        BigDecimal saldoAnterior,
        BigDecimal saldoNuevo
) {
}
