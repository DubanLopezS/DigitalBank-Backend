package com.fabricaescuela.digitalbank.cuenta.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class ClienteNoAutorizadoException extends ApiException {
    public ClienteNoAutorizadoException() {
        super(HttpStatus.FORBIDDEN, "No puedes abrir cuentas a nombre de otro cliente");
    }
}