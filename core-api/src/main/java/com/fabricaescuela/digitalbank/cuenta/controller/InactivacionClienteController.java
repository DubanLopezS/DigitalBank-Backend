package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.cuenta.dto.InactivacionClienteRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.InactivacionClienteResponse;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.IInactivacionClienteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// HU-04. Vive en core-api porque revisa saldos y bloquea cuentas en la misma transacción (ADR-0015).
@RestController
@RequestMapping("/api/v1/clientes")
public class InactivacionClienteController {

    private final IInactivacionClienteService inactivacionClienteService;

    public InactivacionClienteController(IInactivacionClienteService inactivacionClienteService) {
        this.inactivacionClienteService = inactivacionClienteService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{clienteId}/inactivar")
    public ResponseEntity<InactivacionClienteResponse> inactivar(@PathVariable UUID clienteId,
                                                                @Valid @RequestBody InactivacionClienteRequest request,
                                                                Authentication authentication) {
        UUID usuarioId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(inactivacionClienteService.inactivar(clienteId, usuarioId, request));
    }
}
