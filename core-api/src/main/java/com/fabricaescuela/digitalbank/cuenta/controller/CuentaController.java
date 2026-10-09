package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.cuenta.dto.AperturaCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cuentas")
public class CuentaController {

    private final ICuentaService cuentaService;

    public CuentaController(ICuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @PostMapping
    public ResponseEntity<CuentaResponse> abrirCuenta(
            @Valid @RequestBody AperturaCuentaRequest request,
            Authentication authentication) {

        UUID clienteIdAutenticado = (UUID) authentication.getDetails();
        CuentaResponse response = cuentaService.abrirCuenta(request, clienteIdAutenticado);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}