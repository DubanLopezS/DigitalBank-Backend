package com.fabricaescuela.digitalbank.cuenta.interfaces;

import com.fabricaescuela.digitalbank.cuenta.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.TransaccionResponse;

import java.util.UUID;

public interface DepositoService {
    TransaccionResponse registrarDeposito(UUID cuentaId, DepositoRequest request);
}
