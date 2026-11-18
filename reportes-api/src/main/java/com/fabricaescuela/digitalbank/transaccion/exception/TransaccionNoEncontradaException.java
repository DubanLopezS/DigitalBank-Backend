package com.fabricaescuela.digitalbank.transaccion.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class TransaccionNoEncontradaException extends ApiException {
    public TransaccionNoEncontradaException() {
        super(HttpStatus.NOT_FOUND, "TRANSACCION_NO_ENCONTRADA", "Transacción no encontrada");
    }
}
