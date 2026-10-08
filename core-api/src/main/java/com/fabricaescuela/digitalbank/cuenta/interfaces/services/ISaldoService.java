package com.fabricaescuela.digitalbank.cuenta.interfaces.services;

import com.fabricaescuela.digitalbank.cuenta.dto.SaldoResponse;

import java.util.UUID;

public interface ISaldoService {
    SaldoResponse consultarSaldo(String numeroCuenta, UUID usuarioId, String rol);
}
