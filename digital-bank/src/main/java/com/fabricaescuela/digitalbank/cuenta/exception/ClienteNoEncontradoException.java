package com.fabricaescuela.digitalbank.cuenta.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ClienteNoEncontradoException extends ApiException {
    public ClienteNoEncontradoException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}