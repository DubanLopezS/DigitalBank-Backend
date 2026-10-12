package com.fabricaescuela.digitalbank.transaccion.interfaces.services;

import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaResponse;

import java.util.UUID;

public interface ITransferenciaService {
    TransferenciaResponse registrarTransferencia(UUID cuentaId, TransferenciaRequest request, UUID clienteId);
}
