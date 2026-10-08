package com.fabricaescuela.digitalbank.cliente.interfaces.services;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteResumenResponse;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;

import java.util.Optional;

public interface IClienteService {

    Optional<ClienteResumenResponse> obtenerResumenPorDocumento(TipoDocumento tipoDocumento, String numeroDocumento);
}