package com.fabricaescuela.digitalbank.transaccion.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.UUID;

/**
 * Mismo JSON que DetalleTransaccionClienteResponse más usuarioId, que puede ser nulo
 * en las transacciones registradas antes de la V6.
 */
public record DetalleTransaccionAdminResponse(
        @JsonUnwrapped DetalleTransaccionClienteResponse detalle,
        UUID usuarioId
) implements DetalleTransaccionResponse {

    public static DetalleTransaccionAdminResponse from(DetalleTransaccion detalle) {
        return new DetalleTransaccionAdminResponse(
                DetalleTransaccionClienteResponse.from(detalle), detalle.usuarioId());
    }
}
