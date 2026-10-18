package com.fabricaescuela.digitalbank.movimiento.controller;

import com.fabricaescuela.digitalbank.core.config.SecurityConfig;
import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
import com.fabricaescuela.digitalbank.movimiento.dto.MovimientoResponse;
import com.fabricaescuela.digitalbank.movimiento.interfaces.repositories.IMovimientoRepository;
import com.fabricaescuela.digitalbank.movimiento.service.MovimientoServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MovimientoController.class)
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class, MovimientoServiceImpl.class})
@DisplayName("MovimientoController - historial de movimientos por HTTP")
class MovimientoControllerTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final String RUTA = "/api/reportes/cuentas/" + CUENTA_ID + "/movimientos";
    private static final String TOKEN = "token-de-prueba";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private IMovimientoRepository movimientoRepository;

    @Test
    @DisplayName("Sin token responde 401")
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token no proporcionado o inválido"));
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("Un CLIENTE que consulta una cuenta ajena recibe 403 con el cuerpo de acceso denegado")
    void clienteDeOtraCuentaRecibe403() throws Exception {
        // Arrange
        autenticarComo("CLIENTE", UUID.randomUUID());
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.of(UUID.randomUUID()));

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Acceso no autorizado"))
                .andExpect(jsonPath("$.path").value(RUTA));
        verify(movimientoRepository, never()).findMovimientos(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Un CLIENTE consulta su cuenta con la paginacion por defecto")
    void clienteConsultaSuCuenta() throws Exception {
        // Arrange
        UUID clienteId = UUID.randomUUID();
        UUID transferenciaId = UUID.randomUUID();
        autenticarComo("CLIENTE", clienteId);
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.of(clienteId));
        when(movimientoRepository.findMovimientos(CUENTA_ID, null, null, null, 0, 20))
                .thenReturn(List.of(transferenciaSaliente(transferenciaId)));
        when(movimientoRepository.contarMovimientos(CUENTA_ID, null, null, null)).thenReturn(1L);

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenido[0].tipo").value("TRANSFERENCIA"))
                .andExpect(jsonPath("$.contenido[0].sentido").value("SALIENTE"))
                .andExpect(jsonPath("$.contenido[0].fechaHora").value("2026-10-05T10:15:30"))
                .andExpect(jsonPath("$.contenido[0].transferenciaId").value(transferenciaId.toString()))
                .andExpect(jsonPath("$.contenido[0].numeroCuentaRelacionada").value("2222222222"));
    }

    @Test
    @DisplayName("Un ADMIN consulta cualquier cuenta y los filtros llegan con sus nombres")
    void adminConsultaConFiltros() throws Exception {
        // Arrange
        LocalDate inicio = LocalDate.of(2026, 10, 1);
        LocalDate fin = LocalDate.of(2026, 10, 15);
        autenticarComo("ADMIN", null);
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.of(UUID.randomUUID()));
        when(movimientoRepository.findMovimientos(CUENTA_ID, inicio, fin, "TRANSFERENCIA", 1, 5))
                .thenReturn(List.of(transferenciaSaliente(UUID.randomUUID())));
        when(movimientoRepository.contarMovimientos(CUENTA_ID, inicio, fin, "TRANSFERENCIA")).thenReturn(6L);

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN)
                        .param("fecha_inicio", "2026-10-01")
                        .param("fecha_fin", "2026-10-15")
                        .param("tipo", "TRANSFERENCIA")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.contenido.length()").value(1));
    }

    @Test
    @DisplayName("Un ADMIN que consulta una cuenta inexistente recibe 404")
    void cuentaInexistenteResponde404() throws Exception {
        // Arrange
        autenticarComo("ADMIN", null);
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.empty());

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cuenta no encontrada"));
    }

    @Test
    @DisplayName("Si fecha_inicio es posterior a fecha_fin responde 400 con el mensaje del criterio")
    void rangoInvalidoResponde400() throws Exception {
        // Arrange
        autenticarComo("ADMIN", null);

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN)
                        .param("fecha_inicio", "2026-10-20")
                        .param("fecha_fin", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Rango de fechas inválido"));
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("Un tipo que no existe responde 400")
    void tipoInvalidoResponde400() throws Exception {
        // Arrange
        autenticarComo("CAJERO", null);

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN)
                        .param("tipo", "PAGO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tipo de movimiento inválido"));
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("Un size mayor a 100 responde 400")
    void sizeMayorACienResponde400() throws Exception {
        // Arrange
        autenticarComo("ADMIN", null);

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parámetros de paginación inválidos"));
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("Sin movimientos en el rango responde 200 con lista vacia")
    void sinMovimientosResponde200ConListaVacia() throws Exception {
        // Arrange
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fin = LocalDate.of(2026, 1, 31);
        autenticarComo("CAJERO", null);
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.of(UUID.randomUUID()));
        when(movimientoRepository.findMovimientos(CUENTA_ID, inicio, fin, null, 0, 20)).thenReturn(List.of());
        when(movimientoRepository.contarMovimientos(CUENTA_ID, inicio, fin, null)).thenReturn(0L);

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN)
                        .param("fecha_inicio", "2026-01-01")
                        .param("fecha_fin", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private void autenticarComo(String rol, UUID clienteId) {
        when(jwtService.esTokenValido(TOKEN)).thenReturn(true);
        when(jwtService.extraerUsuarioId(TOKEN)).thenReturn(UUID.randomUUID());
        when(jwtService.extraerRol(TOKEN)).thenReturn(rol);
        when(jwtService.extraerClienteId(TOKEN)).thenReturn(clienteId);
    }

    private MovimientoResponse transferenciaSaliente(UUID transferenciaId) {
        return new MovimientoResponse(UUID.randomUUID(), "TRANSFERENCIA", new BigDecimal("30000.00"),
                LocalDateTime.of(2026, 10, 5, 10, 15, 30), new BigDecimal("100000.00"), new BigDecimal("70000.00"),
                "COMPLETADA", null, "SALIENTE", "Pago arriendo", transferenciaId, "2222222222");
    }
}
