package com.fabricaescuela.digitalbank.transaccion.dto;

/**
 * Respuesta del detalle de una transacción. El CLIENTE recibe DetalleTransaccionClienteResponse,
 * sin el campo usuarioId; el ADMIN recibe DetalleTransaccionAdminResponse, con usuarioId aunque sea nulo.
 */
public sealed interface DetalleTransaccionResponse
        permits DetalleTransaccionClienteResponse, DetalleTransaccionAdminResponse {
}
