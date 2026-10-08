package com.fabricaescuela.digitalbank.cuenta.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CuentaNoDisponibleException extends ApiException {
    public CuentaNoDisponibleException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
