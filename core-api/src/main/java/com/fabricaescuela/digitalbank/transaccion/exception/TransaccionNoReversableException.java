package com.fabricaescuela.digitalbank.transaccion.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class TransaccionNoReversableException extends ApiException {
    public TransaccionNoReversableException() {
        super(HttpStatus.CONFLICT, "TRANSACCION_NO_REVERSABLE", "Transacción no reversable");
    }
}
