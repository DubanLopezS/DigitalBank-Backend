package com.fabricaescuela.digitalbank.cliente.interfaces.services;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteDetalleResponse;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;

import java.util.UUID;

public interface IConsultaClienteService {

    ClienteDetalleResponse consultar(TipoDocumento tipoDocumento, String numeroDocumento,
                                    UUID clienteIdAutenticado, String rol);
}