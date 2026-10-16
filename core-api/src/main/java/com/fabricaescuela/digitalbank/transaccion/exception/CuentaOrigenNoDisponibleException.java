package com.fabricaescuela.digitalbank.transaccion.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CuentaOrigenNoDisponibleException extends ApiException {
    public CuentaOrigenNoDisponibleException() {
        super(HttpStatus.CONFLICT, "Cuenta origen no disponible");
    }
}
