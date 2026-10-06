package com.fabricaescuela.digitalbank.cliente.dto;

import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
import com.fabricaescuela.digitalbank.cliente.entity.EstadoCliente;

import java.util.UUID;

public record ClienteResumenResponse(
        UUID id,
        boolean activo
) {
    public static ClienteResumenResponse from(Cliente cliente) {
        return new ClienteResumenResponse(
                cliente.getId(),
                cliente.getEstado() == EstadoCliente.ACTIVO
        );
    }
}
