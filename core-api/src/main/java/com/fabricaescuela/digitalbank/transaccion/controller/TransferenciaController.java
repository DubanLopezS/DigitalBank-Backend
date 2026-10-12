package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaResponse;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.ITransferenciaService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cuentas")
public class TransferenciaController {

    private final ITransferenciaService transferenciaService;

    public TransferenciaController(ITransferenciaService transferenciaService) {
        this.transferenciaService = transferenciaService;
    }

    @PostMapping("/{cuentaId}/transferencias")
    public ResponseEntity<TransferenciaResponse> transferir(@PathVariable UUID cuentaId,
                                                            @Valid @RequestBody TransferenciaRequest request,
                                                            Authentication authentication) {
        UUID clienteId = (UUID) authentication.getDetails();
        TransferenciaResponse response = transferenciaService.registrarTransferencia(cuentaId, request, clienteId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
