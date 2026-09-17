package com.fabricaescuela.digitalbank.cuenta.dto;

import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CuentaResponse(
        UUID id,
        UUID clienteId,
        String numeroCuenta,
        TipoCuenta tipoCuenta,
        BigDecimal saldoContable,
        BigDecimal retencion,
        BigDecimal saldoDisponible,
        String estado,
        LocalDateTime fechaApertura
) {
    public static CuentaResponse from(Cuenta cuenta) {
        return new CuentaResponse(
                cuenta.getId(),
                cuenta.getClienteId(),
                cuenta.getNumeroCuenta(),
                cuenta.getTipoCuenta(),
                cuenta.getSaldoContable(),
                cuenta.getRetencion(),
                cuenta.getSaldoContable().subtract(cuenta.getRetencion()),
                cuenta.getEstado().name(),
                cuenta.getFechaApertura()
        );
    }
}
