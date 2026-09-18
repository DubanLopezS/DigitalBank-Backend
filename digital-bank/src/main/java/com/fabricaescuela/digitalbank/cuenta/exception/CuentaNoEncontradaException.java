package com.fabricaescuela.digitalbank.cuenta.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CuentaNoEncontradaException extends ApiException {
    public CuentaNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "Cuenta no encontrada");
    }
}
