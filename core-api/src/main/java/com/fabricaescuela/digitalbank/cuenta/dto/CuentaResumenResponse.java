package com.fabricaescuela.digitalbank.cuenta.dto;

import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;

import java.math.BigDecimal;
import java.util.UUID;

public record CuentaResumenResponse(
        UUID id,
        String numeroCuenta,
        TipoCuenta tipoCuenta,
        BigDecimal saldoDisponible,
        EstadoCuenta estado
) {

    public static CuentaResumenResponse from(Cuenta cuenta) {
        return new CuentaResumenResponse(
                cuenta.getId(),
                cuenta.getNumeroCuenta(),
                cuenta.getTipoCuenta(),
                cuenta.getSaldoDisponible(),
                cuenta.getEstado());
    }
}