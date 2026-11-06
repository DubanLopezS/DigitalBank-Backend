package com.fabricaescuela.digitalbank.movimiento.exception;

import com.fabricaescuela.digitalbank.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class RangoFechasInvalidoException extends ApiException {
    public RangoFechasInvalidoException() {
        super(HttpStatus.BAD_REQUEST, "RANGO_FECHAS_INVALIDO", "Rango de fechas inválido");
    }
}
