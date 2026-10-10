package com.fabricaescuela.digitalbank.cliente.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ClienteMenorDeEdadException extends ApiException {
    public ClienteMenorDeEdadException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
