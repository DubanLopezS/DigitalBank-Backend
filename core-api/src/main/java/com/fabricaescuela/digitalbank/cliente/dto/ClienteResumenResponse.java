package com.fabricaescuela.digitalbank.cliente.dto;

import java.util.UUID;

public record ClienteResumenResponse(
        UUID id,
        boolean activo
) {
}