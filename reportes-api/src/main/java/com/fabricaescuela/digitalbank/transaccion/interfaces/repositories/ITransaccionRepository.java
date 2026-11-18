package com.fabricaescuela.digitalbank.transaccion.interfaces.repositories;

import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccion;

import java.util.Optional;
import java.util.UUID;

public interface ITransaccionRepository {

    Optional<DetalleTransaccion> findDetalleById(UUID transaccionId);
}
