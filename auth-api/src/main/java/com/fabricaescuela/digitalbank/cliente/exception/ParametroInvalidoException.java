package com.fabricaescuela.digitalbank.cliente.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ParametroInvalidoException extends ApiException {

    public ParametroInvalidoException(String message) {
        super(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", message);
    }
}