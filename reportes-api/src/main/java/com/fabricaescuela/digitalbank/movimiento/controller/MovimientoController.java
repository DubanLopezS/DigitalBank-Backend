package com.fabricaescuela.digitalbank.movimiento.controller;

import com.fabricaescuela.digitalbank.movimiento.dto.HistorialMovimientosResponse;
import com.fabricaescuela.digitalbank.movimiento.interfaces.services.IMovimientoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/reportes/cuentas")
@Tag(name = "Reportes", description = "Consultas de solo lectura sobre las cuentas")
public class MovimientoController {

    private final IMovimientoService movimientoService;

    public MovimientoController(IMovimientoService movimientoService) {
        this.movimientoService = movimientoService;
    }

    @Operation(summary = "Historial de movimientos de una cuenta",
            description = "ADMIN y CAJERO pueden consultar cualquier cuenta; un CLIENTE solo las suyas. "
                    + "Los movimientos vienen del más reciente al más antiguo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de movimientos, puede venir vacía"),
            @ApiResponse(responseCode = "400", description = "Rango de fechas, tipo o paginación inválidos"),
            @ApiResponse(responseCode = "401", description = "Token no proporcionado o inválido"),
            @ApiResponse(responseCode = "403", description = "La cuenta no es del cliente autenticado"),
            @ApiResponse(responseCode = "404", description = "Cuenta no encontrada (solo ADMIN y CAJERO)")
    })
    @GetMapping("/{cuentaId}/movimientos")
    public ResponseEntity<HistorialMovimientosResponse> consultarMovimientos(
            @PathVariable UUID cuentaId,
            @Parameter(description = "Fecha inicial, formato yyyy-MM-dd")
            @RequestParam(name = "fecha_inicio", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @Parameter(description = "Fecha final, formato yyyy-MM-dd. Incluye todo ese día")
            @RequestParam(name = "fecha_fin", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @Parameter(description = "DEPOSITO, RETIRO, TRANSFERENCIA o REVERSO")
            @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página, máximo 100")
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        UUID clienteId = (UUID) authentication.getDetails();
        String rol = obtenerRol(authentication);

        HistorialMovimientosResponse response = movimientoService.consultarMovimientos(
                cuentaId, fechaInicio, fechaFin, tipo, page, size, clienteId, rol);
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
