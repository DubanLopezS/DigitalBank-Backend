package com.fabricaescuela.digitalbank.cuenta.interfaces.services;

import com.fabricaescuela.digitalbank.cuenta.dto.AperturaCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.dto.MovimientoSaldoResponse;

import java.math.BigDecimal;
import java.util.UUID;

public interface ICuentaService {
    CuentaResponse abrirCuenta(AperturaCuentaRequest request, UUID clienteIdAutenticado);

    MovimientoSaldoResponse acreditar(UUID cuentaId, BigDecimal monto);
}
