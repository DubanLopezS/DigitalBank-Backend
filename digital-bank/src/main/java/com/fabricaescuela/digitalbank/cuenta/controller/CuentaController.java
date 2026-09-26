package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.auth.interfaces.UsuarioQueryService;
import com.fabricaescuela.digitalbank.cuenta.dto.AperturaCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.CuentaService;
import com.fabricaescuela.digitalbank.cuenta.interfaces.DepositoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cuentas")
public class CuentaController {

    private final CuentaService cuentaService;
    private final UsuarioQueryService usuarioQueryService;
    private final DepositoService depositoService;

    public CuentaController(CuentaService cuentaService, UsuarioQueryService usuarioQueryService,
                            DepositoService depositoService) {
        this.cuentaService = cuentaService;
        this.usuarioQueryService = usuarioQueryService;
        this.depositoService = depositoService;
    }

    @PreAuthorize("hasRole('CLIENTE')")
    @PostMapping
    public ResponseEntity<CuentaResponse> abrirCuenta(
            @Valid @RequestBody AperturaCuentaRequest request,
            Authentication authentication) {

        UUID usuarioId = (UUID) authentication.getPrincipal();
        UUID clienteIdAutenticado = usuarioQueryService.obtenerClienteId(usuarioId)
                .orElseThrow(ClienteNoAutorizadoException::new);

        CuentaResponse response = cuentaService.abrirCuenta(request, clienteIdAutenticado);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    @PostMapping("/{cuentaId}/depositos")
    public ResponseEntity<TransaccionResponse> depositar(@PathVariable UUID cuentaId,
                                                            @Valid @RequestBody DepositoRequest request) {
        TransaccionResponse response = depositoService.registrarDeposito(cuentaId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
