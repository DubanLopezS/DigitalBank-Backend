package com.fabricaescuela.digitalbank.transaccion.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transacción tal como la lee el repositorio, con las cuentas origen y destino ya resueltas.
 * Lleva el cliente titular de cada cuenta para que el servicio decida el acceso;
 * no se devuelve tal cual en la respuesta.
 */
public record DetalleTransaccion(
        UUID id,
        String tipo,
        BigDecimal monto,
        String estado,
        LocalDateTime fechaHora,
        CuentaTransaccion cuentaOrigen,
        CuentaTransaccion cuentaDestino,
        UUID transaccionRelacionadaId,
        String origen,
        String descripcion,
        UUID usuarioId
) {

    public record CuentaTransaccion(String numeroCuenta, UUID clienteId) {
    }
}
