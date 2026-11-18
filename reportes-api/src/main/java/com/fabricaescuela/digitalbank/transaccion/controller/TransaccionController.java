package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.ITransaccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reportes/transacciones")
@Tag(name = "Reportes", description = "Consultas de solo lectura sobre las cuentas")
public class TransaccionController {

    private static final Logger log = LoggerFactory.getLogger(TransaccionController.class);

    private final ITransaccionService transaccionService;

    public TransaccionController(ITransaccionService transaccionService) {
        this.transaccionService = transaccionService;
    }

    @Operation(summary = "Detalle de una transacción",
            description = "ADMIN puede consultar cualquier transacción y ve el usuario que la originó. "
                    + "Un CLIENTE solo las que tienen como origen o destino una cuenta suya, sin el usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de la transacción"),
            @ApiResponse(responseCode = "400", description = "El id no es un UUID válido"),
            @ApiResponse(responseCode = "401", description = "Token no proporcionado o inválido"),
            @ApiResponse(responseCode = "403", description = "La transacción no es del cliente autenticado, o el rol no es CLIENTE ni ADMIN"),
            @ApiResponse(responseCode = "404", description = "Transacción no encontrada (solo ADMIN)")
    })
    @PreAuthorize("hasAnyRole('CLIENTE','ADMIN')")
    @GetMapping("/{transaccionId}")
    public ResponseEntity<DetalleTransaccionResponse> consultarDetalle(@PathVariable UUID transaccionId,
                                                                       Authentication authentication) {
        UUID clienteId = (UUID) authentication.getDetails();
        String rol = obtenerRol(authentication);

        try {
            return ResponseEntity.ok(transaccionService.consultarDetalle(transaccionId, clienteId, rol));
        } catch (AccesoNoAutorizadoException ex) {
            log.warn("evento=ACCESO_TRANSACCION_DENEGADO status={} transaccionId={} usuarioId={} rol={}",
                    ex.getStatus().value(), transaccionId, authentication.getName(), rol);
            throw ex;
        }
    }

    private String obtenerRol(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(authority -> authority.replace("ROLE_", ""))
                .orElseThrow();
    }
}
