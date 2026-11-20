package com.fabricaescuela.digitalbank.transaccion.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DetalleTransaccionClienteResponse(
        UUID id,
        String tipo,
        BigDecimal monto,
        String estado,
        LocalDateTime fechaHora,
        String numeroCuentaOrigen,
        String numeroCuentaDestino,
        UUID transaccionRelacionadaId,
        String origen,
        String descripcion
) implements DetalleTransaccionResponse {

    public static DetalleTransaccionClienteResponse from(DetalleTransaccion detalle) {
        return new DetalleTransaccionClienteResponse(
                detalle.id(),
                detalle.tipo(),
                detalle.monto(),
                detalle.estado(),
                detalle.fechaHora(),
                numeroDe(detalle.cuentaOrigen()),
                numeroDe(detalle.cuentaDestino()),
                detalle.transaccionRelacionadaId(),
                detalle.origen(),
                detalle.descripcion()
        );
    }

    private static String numeroDe(DetalleTransaccion.CuentaTransaccion cuenta) {
        return cuenta != null ? cuenta.numeroCuenta() : null;
    }
}
