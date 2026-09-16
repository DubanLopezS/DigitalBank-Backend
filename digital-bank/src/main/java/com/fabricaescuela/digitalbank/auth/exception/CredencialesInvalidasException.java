package com.fabricaescuela.digitalbank.auth.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CredencialesInvalidasException extends ApiException {
    public CredencialesInvalidasException() {
        super(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
    }
}