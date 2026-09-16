package com.fabricaescuela.digitalbank.cuenta.interfaces;

import com.fabricaescuela.digitalbank.cuenta.dto.SaldoResponse;

import java.util.UUID;

public interface SaldoService {
    SaldoResponse consultarSaldo(String numeroCuenta, UUID usuarioId, String rol);
}
