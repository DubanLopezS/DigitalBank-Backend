package com.fabricaescuela.digitalbank.movimiento.interfaces.repositories;

import com.fabricaescuela.digitalbank.movimiento.dto.MovimientoResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IMovimientoRepository {

    Optional<UUID> findClienteIdByCuentaId(UUID cuentaId);

    List<MovimientoResponse> findMovimientos(UUID cuentaId, LocalDate fechaInicio, LocalDate fechaFin,
                                             String tipo, int page, int size);

    long contarMovimientos(UUID cuentaId, LocalDate fechaInicio, LocalDate fechaFin, String tipo);
}
