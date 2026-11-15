package com.fabricaescuela.digitalbank.cliente.controller;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteActualizacionRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteRegistroRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResponse;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    private final IClienteService clienteService;

    public ClienteController(IClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ClienteResponse> registrar(@Valid @RequestBody ClienteRegistroRequest request) {
        ClienteResponse response = clienteService.registrarCliente(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // El principal lo pone JwtAuthenticationFilter y es el usuarioId del token.
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{clienteId}")
    public ResponseEntity<ClienteResponse> actualizar(@PathVariable UUID clienteId,
                                                      @Valid @RequestBody ClienteActualizacionRequest request,
                                                      @AuthenticationPrincipal UUID usuarioId) {
        ClienteResponse response = clienteService.actualizarCliente(clienteId, request, usuarioId);
        return ResponseEntity.ok(response);
    }
}