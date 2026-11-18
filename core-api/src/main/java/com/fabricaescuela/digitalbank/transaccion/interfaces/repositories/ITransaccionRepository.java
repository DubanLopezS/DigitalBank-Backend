package com.fabricaescuela.digitalbank.transaccion.interfaces.repositories;

import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ITransaccionRepository {

    Transaccion save(Transaccion transaccion);

    Optional<Transaccion> findById(UUID id);

    Optional<Transaccion> findByIdForUpdate(UUID id);

    List<Transaccion> findByTransferenciaIdForUpdate(UUID transferenciaId);
}
