package com.fabricaescuela.digitalbank.cuenta.dto;

import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoAccionCuenta;

import java.time.LocalDateTime;
import java.util.UUID;

public record CambioEstadoCuentaResponse(
        String numeroCuenta,
        TipoAccionCuenta accion,
        EstadoCuenta estadoAnterior,
        EstadoCuenta estadoNuevo,
        String motivo,
        LocalDateTime fechaHora,
        UUID usuarioResponsableId) {
}