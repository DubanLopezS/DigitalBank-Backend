package com.fabricaescuela.digitalbank.transaccion.interfaces.services;

import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccionResponse;

import java.util.UUID;

public interface ITransaccionService {
    DetalleTransaccionResponse consultarDetalle(UUID transaccionId, UUID clienteId, String rol);
}
