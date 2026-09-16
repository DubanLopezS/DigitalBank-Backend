package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.cuenta.dto.SaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.interfaces.SaldoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/cuentas")
public class SaldoController {

    private final SaldoService saldoService;

    public SaldoController(SaldoService saldoService) {
        this.saldoService = saldoService;
    }

    @GetMapping("/{numeroCuenta}/saldo")
    public ResponseEntity<SaldoResponse> consultarSaldo(@PathVariable String numeroCuenta,
                                                          Authentication authentication) {
        UUID usuarioId = (UUID) authentication.getPrincipal();
        String rol = obtenerRol(authentication);

        SaldoResponse response = saldoService.consultarSaldo(numeroCuenta, usuarioId, rol);
        return ResponseEntity.ok(response);
    }

    private String obtenerRol(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(authority -> authority.replace("ROLE_", ""))
                .orElseThrow();
    }
}
