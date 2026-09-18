package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.core.security.JwtService;
import com.fabricaescuela.digitalbank.cuenta.dto.AperturaCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.CuentaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cuentas")
public class CuentaController {

    private static final String PREFIJO_BEARER = "Bearer ";

    private final CuentaService cuentaService;
    private final JwtService jwtService;

    public CuentaController(CuentaService cuentaService, JwtService jwtService) {
        this.cuentaService = cuentaService;
        this.jwtService = jwtService;
    }

    @PostMapping
    public ResponseEntity<CuentaResponse> abrirCuenta(
            @Valid @RequestBody AperturaCuentaRequest request,
            @RequestHeader("Authorization") String authorizationHeader) {

        UUID clienteIdAutenticado = extraerClienteId(authorizationHeader);
        CuentaResponse response = cuentaService.abrirCuenta(request, clienteIdAutenticado);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private UUID extraerClienteId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(PREFIJO_BEARER)) {
            throw new ClienteNoAutorizadoException();
        }
        return jwtService.extraerClienteId(authorizationHeader.substring(PREFIJO_BEARER.length()));
    }
}