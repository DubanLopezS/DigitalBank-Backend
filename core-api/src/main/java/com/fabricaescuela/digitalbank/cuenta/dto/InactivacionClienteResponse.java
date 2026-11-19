package com.fabricaescuela.digitalbank.cuenta.dto;

import com.fabricaescuela.digitalbank.cliente.entity.EstadoCliente;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InactivacionClienteResponse(
        UUID clienteId,
        EstadoCliente estadoAnterior,
        EstadoCliente estadoNuevo,
        List<String> cuentasBloqueadas,
        String motivo,
        LocalDateTime fechaHora,
        UUID usuarioResponsableId) {
}
