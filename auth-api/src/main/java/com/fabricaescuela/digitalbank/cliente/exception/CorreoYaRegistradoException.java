package com.fabricaescuela.digitalbank.cliente.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CorreoYaRegistradoException extends ApiException {
    public CorreoYaRegistradoException(String message) {
        super(HttpStatus.CONFLICT, "CORREO_YA_REGISTRADO", message);
    }
}
