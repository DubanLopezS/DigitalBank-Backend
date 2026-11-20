package com.fabricaescuela.digitalbank.cuenta.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class SaldoReversoInsuficienteException extends ApiException {
    public SaldoReversoInsuficienteException() {
        super(HttpStatus.CONFLICT, "SALDO_INSUFICIENTE", "Saldo insuficiente para reversar");
    }
}
