package com.fabricaescuela.digitalbank.cuenta.interfaces.services;

import com.fabricaescuela.digitalbank.cuenta.dto.CambioEstadoCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CambioEstadoCuentaResponse;

import java.util.UUID;

public interface IEstadoCuentaService {

    CambioEstadoCuentaResponse bloquear(UUID cuentaId, UUID usuarioId, CambioEstadoCuentaRequest request);

    CambioEstadoCuentaResponse desbloquear(UUID cuentaId, UUID usuarioId, CambioEstadoCuentaRequest request);

    CambioEstadoCuentaResponse cerrar(UUID cuentaId, UUID usuarioId, CambioEstadoCuentaRequest request);
}

