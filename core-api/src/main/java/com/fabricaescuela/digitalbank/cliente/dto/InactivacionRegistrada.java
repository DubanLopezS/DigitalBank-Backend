package com.fabricaescuela.digitalbank.cliente.dto;

import java.time.LocalDateTime;
import java.util.UUID;

// Lo que quedó guardado en la auditoría de la inactivación.
public record InactivacionRegistrada(
        UUID usuarioId,
        String motivo,
        LocalDateTime fechaHora
) {
}
