package com.fabricaescuela.digitalbank.transaccion.interfaces.services;

import com.fabricaescuela.digitalbank.transaccion.dto.ReversoRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.ReversoResponse;

import java.util.UUID;

public interface IReversoService {

    ReversoResponse reversar(UUID transaccionId, ReversoRequest request, UUID usuarioId);
}
