package com.fabricaescuela.digitalbank.cliente.dto;

public record CuentaDelClienteResponse(
        String numeroCuenta,
        String tipoCuenta,
        String estado
) {
}