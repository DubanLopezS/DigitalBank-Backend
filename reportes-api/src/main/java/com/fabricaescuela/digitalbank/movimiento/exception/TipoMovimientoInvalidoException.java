package com.fabricaescuela.digitalbank.movimiento.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class TipoMovimientoInvalidoException extends ApiException {
    public TipoMovimientoInvalidoException() {
        super(HttpStatus.BAD_REQUEST, "Tipo de movimiento inválido");
    }
}
