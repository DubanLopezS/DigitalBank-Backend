package com.fabricaescuela.digitalbank.transaccion.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CuentaOrigenDestinoIgualesException extends ApiException {
    public CuentaOrigenDestinoIgualesException() {
        super(HttpStatus.BAD_REQUEST, "CUENTA_ORIGEN_DESTINO_IGUALES", "Cuenta origen y destino no pueden ser iguales");
    }
}
