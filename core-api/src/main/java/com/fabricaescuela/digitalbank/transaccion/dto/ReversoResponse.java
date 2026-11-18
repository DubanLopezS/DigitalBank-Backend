package com.fabricaescuela.digitalbank.transaccion.dto;

import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReversoResponse(
        UUID transaccionOriginalId,
        String tipoOriginal,
        String estadoOriginal,
        BigDecimal monto,
        String motivo,
        UUID usuarioId,
        LocalDateTime fechaHora,
        List<MovimientoReverso> reversos
) {
    public record MovimientoReverso(
            UUID reversoId,
            UUID cuentaId,
            String sentido,
            BigDecimal saldoAnterior,
            BigDecimal saldoNuevo
    ) {
        public static MovimientoReverso from(Transaccion reverso) {
            return new MovimientoReverso(
                    reverso.getId(),
                    reverso.getCuentaId(),
                    reverso.getSentido() != null ? reverso.getSentido().name() : null,
                    reverso.getSaldoAnterior(),
                    reverso.getSaldoNuevo()
            );
        }
    }
}
