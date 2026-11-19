package com.fabricaescuela.digitalbank.cliente.interfaces.services;

import com.fabricaescuela.digitalbank.cliente.dto.InactivacionRegistrada;
import com.fabricaescuela.digitalbank.cliente.entity.EstadoCliente;

import java.util.Optional;
import java.util.UUID;

// Estado del cliente visto desde core-api (HU-04). La tabla cliente es de auth-api:
// core-api solo lee su estado y lo cambia a INACTIVO (ADR-0015).
public interface IClienteEstado {

    // true solo si el cliente existe y está INACTIVO.
    boolean estaInactivo(UUID clienteId);

    // Bloquea la fila del cliente hasta el fin de la transacción y devuelve su estado.
    Optional<EstadoCliente> bloquearYObtenerEstado(UUID clienteId);

    // Cambia el cliente a INACTIVO y guarda la auditoría, en la transacción actual.
    InactivacionRegistrada inactivar(UUID clienteId, UUID usuarioId, String motivo);
}
