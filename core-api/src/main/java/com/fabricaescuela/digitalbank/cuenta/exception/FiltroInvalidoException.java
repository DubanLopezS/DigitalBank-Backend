package com.fabricaescuela.digitalbank.cuenta.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class FiltroInvalidoException extends ApiException {
    public FiltroInvalidoException() {
        super(HttpStatus.BAD_REQUEST, "FILTRO_INVALIDO",
                "El filtro de estado debe ser ACTIVA, BLOQUEADA o CERRADA");
    }
}
