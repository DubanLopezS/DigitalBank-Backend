package com.fabricaescuela.digitalbank.cuenta.interfaces.services;

import com.fabricaescuela.digitalbank.cuenta.dto.InactivacionClienteRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.InactivacionClienteResponse;

import java.util.UUID;

public interface IInactivacionClienteService {
    InactivacionClienteResponse inactivar(UUID clienteId, UUID usuarioId, InactivacionClienteRequest request);
}
