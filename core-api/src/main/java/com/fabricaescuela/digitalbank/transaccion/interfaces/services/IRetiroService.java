package com.fabricaescuela.digitalbank.transaccion.interfaces.services;

import com.fabricaescuela.digitalbank.transaccion.dto.RetiroRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransaccionResponse;

import java.util.UUID;

public interface IRetiroService {
    TransaccionResponse registrarRetiro(UUID cuentaId, RetiroRequest request, UUID clienteId);
}
