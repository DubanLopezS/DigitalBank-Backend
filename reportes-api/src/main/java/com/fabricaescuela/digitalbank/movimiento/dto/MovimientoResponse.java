package com.fabricaescuela.digitalbank.movimiento.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MovimientoResponse(
        UUID id,
        String tipo,
        BigDecimal monto,
        LocalDateTime fechaHora,
        BigDecimal saldoAnterior,
        BigDecimal saldoNuevo,
        String estado,
        String origen,
        String sentido,
        String descripcion,
        UUID transferenciaId,
        String numeroCuentaRelacionada
) {
}
