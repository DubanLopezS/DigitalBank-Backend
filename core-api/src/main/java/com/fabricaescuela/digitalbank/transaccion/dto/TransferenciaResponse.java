package com.fabricaescuela.digitalbank.transaccion.dto;

import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransferenciaResponse(
        UUID id,
        UUID transferenciaId,
        UUID cuentaOrigenId,
        String cuentaDestino,
        BigDecimal monto,
        String descripcion,
        String estado,
        LocalDateTime fechaHora
) {
    public static TransferenciaResponse from(Transaccion salida, String cuentaDestino) {
        return new TransferenciaResponse(
                salida.getId(),
                salida.getTransferenciaId(),
                salida.getCuentaId(),
                cuentaDestino,
                salida.getMonto(),
                salida.getDescripcion(),
                salida.getEstado().name(),
                salida.getFechaHora()
        );
    }
}
