package com.fabricaescuela.digitalbank.transaccion.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CuentaDestinoNoDisponibleException extends ApiException {
    public CuentaDestinoNoDisponibleException() {
        super(HttpStatus.CONFLICT, "Cuenta destino no disponible");
    }
}
