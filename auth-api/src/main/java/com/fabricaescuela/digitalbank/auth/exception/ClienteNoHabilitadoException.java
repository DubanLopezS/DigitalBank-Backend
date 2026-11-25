package com.fabricaescuela.digitalbank.auth.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

// HU-04: un cliente INACTIVO no puede iniciar sesión.
public class ClienteNoHabilitadoException extends ApiException {
    public ClienteNoHabilitadoException() {
        super(HttpStatus.FORBIDDEN, "CLIENTE_NO_HABILITADO", "Cliente no habilitado");
    }
}
