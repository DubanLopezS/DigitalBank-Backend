package com.fabricaescuela.digitalbank.cuenta.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ClienteConSaldosPendientesException extends ApiException {
    public ClienteConSaldosPendientesException() {
        super(HttpStatus.CONFLICT, "CLIENTE_CON_SALDOS_PENDIENTES", "El cliente tiene saldos pendientes");
    }
}
