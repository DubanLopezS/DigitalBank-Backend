package com.fabricaescuela.digitalbank.movimiento.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PaginacionInvalidaException extends ApiException {
    public PaginacionInvalidaException() {
        super(HttpStatus.BAD_REQUEST, "Parámetros de paginación inválidos");
    }
}
