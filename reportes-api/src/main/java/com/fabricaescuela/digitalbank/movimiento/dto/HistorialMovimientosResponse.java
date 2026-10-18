package com.fabricaescuela.digitalbank.movimiento.dto;

import java.util.List;

public record HistorialMovimientosResponse(
        List<MovimientoResponse> contenido,
        int page,
        int size,
        long totalElements
) {
}
