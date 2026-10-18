package com.fabricaescuela.digitalbank.movimiento.interfaces.services;

import com.fabricaescuela.digitalbank.movimiento.dto.HistorialMovimientosResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface IMovimientoService {
    HistorialMovimientosResponse consultarMovimientos(UUID cuentaId, LocalDate fechaInicio, LocalDate fechaFin,
                                                      String tipo, int page, int size, UUID clienteId, String rol);
}
