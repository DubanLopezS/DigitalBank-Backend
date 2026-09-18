package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.cuenta.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.cuenta.interfaces.DepositoService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cuentas")
public class CuentaController {

    private final DepositoService depositoService;

    public CuentaController(DepositoService depositoService) {
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
