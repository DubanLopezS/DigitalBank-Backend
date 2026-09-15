package com.fabricaescuela.digitalbank.auth.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class CuentaBloqueadaException extends ApiException {
    public CuentaBloqueadaException() {
    super(HttpStatus.TOO_MANY_REQUESTS, "Cuenta bloqueada temporalmente por múltiples intentos fallidos");
}
}