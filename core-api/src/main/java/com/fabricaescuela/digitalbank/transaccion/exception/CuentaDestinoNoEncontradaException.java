package com.fabricaescuela.digitalbank.transaccion.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CuentaDestinoNoEncontradaException extends ApiException {
    public CuentaDestinoNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "Cuenta destino no encontrada");
    }
}
