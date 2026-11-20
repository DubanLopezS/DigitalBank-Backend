package com.fabricaescuela.digitalbank.cliente.interfaces.services;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteResumenResponse;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;

import java.util.Optional;
import java.util.UUID;

public interface IClienteConsulta {

    Optional<ClienteResumenResponse> obtenerResumenPorDocumento(TipoDocumento tipoDocumento, String numeroDocumento);

    boolean existePorId(UUID clienteId);

    boolean estaActivoPorId(UUID clienteId);
}