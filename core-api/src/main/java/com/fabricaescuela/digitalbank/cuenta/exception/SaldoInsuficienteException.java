package com.fabricaescuela.digitalbank.cuenta.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class SaldoInsuficienteException extends ApiException {
    public SaldoInsuficienteException() {
        super(HttpStatus.CONFLICT, "Saldo insuficiente");
    }
}
