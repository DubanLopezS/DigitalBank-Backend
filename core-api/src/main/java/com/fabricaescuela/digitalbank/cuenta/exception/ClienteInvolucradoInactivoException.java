package com.fabricaescuela.digitalbank.cuenta.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ClienteInvolucradoInactivoException extends ApiException {
    public ClienteInvolucradoInactivoException() {
        super(HttpStatus.CONFLICT, "CLIENTE_NO_HABILITADO", "Cliente no habilitado");
    }
}
