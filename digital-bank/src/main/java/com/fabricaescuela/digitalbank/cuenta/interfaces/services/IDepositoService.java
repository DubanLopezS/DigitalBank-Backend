package com.fabricaescuela.digitalbank.cuenta.interfaces.services;

import com.fabricaescuela.digitalbank.cuenta.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.TransaccionResponse;

import java.util.UUID;

public interface IDepositoService {
    TransaccionResponse registrarDeposito(UUID cuentaId, DepositoRequest request);
}
