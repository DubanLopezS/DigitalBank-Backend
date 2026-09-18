package com.fabricaescuela.digitalbank.auth.interfaces;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioQueryService {
    Optional<UUID> obtenerClienteId(UUID usuarioId);
}
