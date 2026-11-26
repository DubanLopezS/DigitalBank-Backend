package com.fabricaescuela.digitalbank.transaccion.interfaces.repositories;

import com.fabricaescuela.digitalbank.transaccion.entity.TipoTransaccion;

import java.util.UUID;

public record ResumenTransaccion(TipoTransaccion tipo, UUID transferenciaId) {
}
