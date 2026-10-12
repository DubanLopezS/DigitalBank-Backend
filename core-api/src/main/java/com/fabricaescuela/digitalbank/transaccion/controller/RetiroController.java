package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.transaccion.dto.RetiroRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IRetiroService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cuentas")
public class RetiroController {

    private final IRetiroService retiroService;

    public RetiroController(IRetiroService retiroService) {
        this.retiroService = retiroService;
    }

    @PostMapping("/{cuentaId}/retiros")
    public ResponseEntity<TransaccionResponse> retirar(@PathVariable UUID cuentaId,
                                                       @Valid @RequestBody RetiroRequest request,
                                                       Authentication authentication) {
        UUID clienteId = (UUID) authentication.getDetails();
        TransaccionResponse response = retiroService.registrarRetiro(cuentaId, request, clienteId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
