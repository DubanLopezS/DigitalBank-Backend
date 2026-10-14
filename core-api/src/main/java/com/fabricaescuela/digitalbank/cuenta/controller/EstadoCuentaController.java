package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.cuenta.dto.CambioEstadoCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CambioEstadoCuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.IEstadoCuentaService;
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

@RestController
@RequestMapping("/api/cuentas")
public class EstadoCuentaController {

    private final IEstadoCuentaService estadoCuentaService;

    public EstadoCuentaController(IEstadoCuentaService estadoCuentaService) {
        this.estadoCuentaService = estadoCuentaService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{cuentaId}/bloquear")
    public ResponseEntity<CambioEstadoCuentaResponse> bloquear(@PathVariable UUID cuentaId,
                                                            @Valid @RequestBody CambioEstadoCuentaRequest request,
                                                            Authentication authentication) {
        UUID usuarioId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(estadoCuentaService.bloquear(cuentaId, usuarioId, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{cuentaId}/desbloquear")
    public ResponseEntity<CambioEstadoCuentaResponse> desbloquear(@PathVariable UUID cuentaId,
                                                                @Valid @RequestBody CambioEstadoCuentaRequest request,
                                                                Authentication authentication) {
        UUID usuarioId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(estadoCuentaService.desbloquear(cuentaId, usuarioId, request));
    }
}