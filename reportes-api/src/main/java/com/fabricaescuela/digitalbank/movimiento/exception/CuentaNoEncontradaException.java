package com.fabricaescuela.digitalbank.movimiento.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CuentaNoEncontradaException extends ApiException {
    public CuentaNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "CUENTA_NO_ENCONTRADA", "Cuenta no encontrada");
    }
}
