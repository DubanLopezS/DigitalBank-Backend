package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.transaccion.dto.ReversoRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.ReversoResponse;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IReversoService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transacciones")
public class ReversoController {

    private final IReversoService reversoService;

    public ReversoController(IReversoService reversoService) {
        this.reversoService = reversoService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{transaccionId}/reversos")
    public ResponseEntity<ReversoResponse> reversar(@PathVariable UUID transaccionId,
                                                    @Valid @RequestBody ReversoRequest request,
                                                    Authentication authentication) {
        UUID usuarioId = (UUID) authentication.getPrincipal();
        ReversoResponse response = reversoService.reversar(transaccionId, request, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
