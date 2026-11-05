package com.fabricaescuela.digitalbank.core.exception;

import org.springframework.http.HttpStatus;

public class AccesoNoAutorizadoException extends ApiException {
    public AccesoNoAutorizadoException() {
        super(HttpStatus.FORBIDDEN, "ACCESO_NO_AUTORIZADO", "Acceso no autorizado");
    }
}
