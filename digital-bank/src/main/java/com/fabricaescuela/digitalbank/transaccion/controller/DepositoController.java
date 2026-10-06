package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.transaccion.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IDepositoService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cuentas")
public class DepositoController {

    private final IDepositoService depositoService;

    public DepositoController(IDepositoService depositoService) {
        this.depositoService = depositoService;
    }

    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    @PostMapping("/{cuentaId}/depositos")
    public ResponseEntity<TransaccionResponse> depositar(@PathVariable UUID cuentaId,
                                                            @Valid @RequestBody DepositoRequest request) {
        TransaccionResponse response = depositoService.registrarDeposito(cuentaId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
