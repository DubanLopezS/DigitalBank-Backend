package com.fabricaescuela.digitalbank.cliente.controller;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteDetalleResponse;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IConsultaClienteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clientes")
public class ConsultaClienteController {

    private static final String PREFIJO_ROL = "ROLE_";

    private final IConsultaClienteService consultaClienteService;

    public ConsultaClienteController(IConsultaClienteService consultaClienteService) {
        this.consultaClienteService = consultaClienteService;
    }

    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN','CAJERO')")
    @GetMapping
    public ResponseEntity<ClienteDetalleResponse> consultar(@RequestParam TipoDocumento tipoDocumento,
                                                            @RequestParam String numeroDocumento,
                                                            Authentication authentication) {
        UUID clienteIdAutenticado = authentication.getDetails() instanceof UUID id ? id : null;
        ClienteDetalleResponse response = consultaClienteService.consultar(
                tipoDocumento, numeroDocumento, clienteIdAutenticado, obtenerRol(authentication));
        return ResponseEntity.ok(response);
    }

    private String obtenerRol(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority().replaceFirst("^" + PREFIJO_ROL, ""))
                .orElse("");
    }
}