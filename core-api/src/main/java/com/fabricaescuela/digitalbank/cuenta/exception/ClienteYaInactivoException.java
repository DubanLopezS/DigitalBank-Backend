package com.fabricaescuela.digitalbank.cuenta.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ClienteYaInactivoException extends ApiException {
    public ClienteYaInactivoException() {
        super(HttpStatus.CONFLICT, "CLIENTE_YA_INACTIVO", "El cliente ya está inactivo");
    }
}
