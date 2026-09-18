package com.fabricaescuela.digitalbank.cuenta.dto;

import com.fabricaescuela.digitalbank.cuenta.entity.Transaccion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransaccionResponse(
        UUID id,
        UUID cuentaId,
        String tipo,
        BigDecimal monto,
        BigDecimal saldoAnterior,
        BigDecimal saldoNuevo,
        String origen,
        String estado,
        LocalDateTime fechaHora
) {
    public static TransaccionResponse from(Transaccion transaccion) {
        return new TransaccionResponse(
                transaccion.getId(),
                transaccion.getCuentaId(),
                transaccion.getTipo().name(),
                transaccion.getMonto(),
                transaccion.getSaldoAnterior(),
                transaccion.getSaldoNuevo(),
                transaccion.getOrigen().name(),
                transaccion.getEstado().name(),
                transaccion.getFechaHora()
        );
    }
}
