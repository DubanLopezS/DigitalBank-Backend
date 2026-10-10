package com.fabricaescuela.digitalbank.cliente.interfaces.services;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteRegistroRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResponse;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResumenResponse;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;

import java.util.Optional;

public interface IClienteService {
    ClienteResponse registrarCliente(ClienteRegistroRequest request);

    Optional<ClienteResumenResponse> obtenerResumenPorDocumento(TipoDocumento tipoDocumento, String numeroDocumento);
}
