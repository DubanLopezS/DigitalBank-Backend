package com.fabricaescuela.digitalbank.auth.interfaces.services;

import java.util.Optional;
import java.util.UUID;

public interface IUsuarioQueryService {
    Optional<UUID> obtenerClienteId(UUID usuarioId);
}
