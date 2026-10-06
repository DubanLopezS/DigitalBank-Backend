package com.fabricaescuela.digitalbank.transaccion.interfaces.services;

import com.fabricaescuela.digitalbank.transaccion.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransaccionResponse;

import java.util.UUID;

public interface IDepositoService {
    TransaccionResponse registrarDeposito(UUID cuentaId, DepositoRequest request);
}
