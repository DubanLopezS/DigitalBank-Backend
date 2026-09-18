package com.fabricaescuela.digitalbank.cuenta.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ClienteNoHabilitadoException extends ApiException {
    public ClienteNoHabilitadoException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}