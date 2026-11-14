package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResumenResponse;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.IListadoCuentasService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clientes/{clienteId}/cuentas")
public class ClienteCuentasController {

    private final IListadoCuentasService listadoCuentasService;

    public ClienteCuentasController(IListadoCuentasService listadoCuentasService) {
        this.listadoCuentasService = listadoCuentasService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN','CAJERO')")
    public ResponseEntity<List<CuentaResumenResponse>> listar(@PathVariable UUID clienteId,
                                                            @RequestParam(required = false) String estado,
                                                            Authentication authentication) {
        UUID clienteIdAutenticado = (UUID) authentication.getDetails();
        return ResponseEntity.ok(listadoCuentasService.listar(clienteId, estado, clienteIdAutenticado));
    }
}