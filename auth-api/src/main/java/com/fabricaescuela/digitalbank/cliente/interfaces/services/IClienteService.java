package com.fabricaescuela.digitalbank.cliente.interfaces.services;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteActualizacionRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteRegistroRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResponse;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResumenResponse;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;

import java.util.Optional;
import java.util.UUID;

public interface IClienteService {
    ClienteResponse registrarCliente(ClienteRegistroRequest request);

    ClienteResponse actualizarCliente(UUID clienteId, ClienteActualizacionRequest request, UUID usuarioId);

    Optional<ClienteResumenResponse> obtenerResumenPorDocumento(TipoDocumento tipoDocumento, String numeroDocumento);
}
