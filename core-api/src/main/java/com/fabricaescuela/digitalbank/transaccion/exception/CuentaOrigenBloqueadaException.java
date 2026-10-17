package com.fabricaescuela.digitalbank.transaccion.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CuentaOrigenBloqueadaException extends ApiException {
    public CuentaOrigenBloqueadaException() {
        super(HttpStatus.CONFLICT, "Cuenta origen bloqueada");
    }
}
