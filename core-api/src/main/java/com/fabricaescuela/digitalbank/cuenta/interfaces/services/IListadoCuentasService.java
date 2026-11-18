package com.fabricaescuela.digitalbank.cuenta.interfaces.services;

import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResumenResponse;

import java.util.List;
import java.util.UUID;

public interface IListadoCuentasService {

    List<CuentaResumenResponse> listar(UUID clienteId, String estado, UUID clienteIdAutenticado, String rol);
}