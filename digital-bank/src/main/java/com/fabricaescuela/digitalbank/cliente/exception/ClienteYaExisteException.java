package com.fabricaescuela.digitalbank.cliente.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ClienteYaExisteException extends ApiException {
    public ClienteYaExisteException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}