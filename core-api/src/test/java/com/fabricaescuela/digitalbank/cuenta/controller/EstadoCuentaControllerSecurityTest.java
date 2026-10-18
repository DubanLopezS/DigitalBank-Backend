package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.core.config.SecurityConfig;
import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
import com.fabricaescuela.digitalbank.cuenta.dto.CambioEstadoCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CambioEstadoCuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoAccionCuenta;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.IEstadoCuentaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EstadoCuentaController.class)
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@DisplayName("EstadoCuentaController - autorizacion por rol")
class EstadoCuentaControllerSecurityTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final String MOTIVO = "Actividad sospechosa detectada";
    private static final String BODY_VALIDO = "{\"motivo\":\"" + MOTIVO + "\"}";
    private static final String TOKEN = "token-de-prueba";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private IEstadoCuentaService estadoCuentaService;

    @Test
    @DisplayName("Un CLIENTE recibe 403 al bloquear y el servicio no se invoca")
    void clienteRecibe403AlBloquear() throws Exception {
        // Arrange
        String ruta = "/api/cuentas/" + CUENTA_ID + "/bloquear";
        autenticarComo(UUID.randomUUID(), "CLIENTE");

        // Act + Assert
        mockMvc.perform(post(ruta)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Acceso no autorizado"))
                .andExpect(jsonPath("$.path").value(ruta));
        verifyNoInteractions(estadoCuentaService);
    }

    @Test
    @DisplayName("Un CLIENTE recibe 403 al desbloquear y el servicio no se invoca")
    void clienteRecibe403AlDesbloquear() throws Exception {
        // Arrange
        String ruta = "/api/cuentas/" + CUENTA_ID + "/desbloquear";
        autenticarComo(UUID.randomUUID(), "CLIENTE");

        // Act + Assert
        mockMvc.perform(post(ruta)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Acceso no autorizado"))
                .andExpect(jsonPath("$.path").value(ruta));
        verifyNoInteractions(estadoCuentaService);
    }

    @Test
    @DisplayName("Un CLIENTE recibe 403 al cerrar y el servicio no se invoca")
    void clienteRecibe403AlCerrar() throws Exception {
        // Arrange
        String ruta = "/api/cuentas/" + CUENTA_ID + "/cerrar";
        autenticarComo(UUID.randomUUID(), "CLIENTE");

        // Act + Assert
        mockMvc.perform(post(ruta)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Acceso no autorizado"))
                .andExpect(jsonPath("$.path").value(ruta));
        verifyNoInteractions(estadoCuentaService);
    }

    @Test
    @DisplayName("Un ADMIN puede bloquear y el responsable se toma del token")
    void adminPuedeBloquear() throws Exception {
        // Arrange
        UUID adminId = UUID.randomUUID();
        CambioEstadoCuentaRequest request = new CambioEstadoCuentaRequest(MOTIVO);
        autenticarComo(adminId, "ADMIN");
        when(estadoCuentaService.bloquear(CUENTA_ID, adminId, request))
                .thenReturn(respuesta(TipoAccionCuenta.BLOQUEO, EstadoCuenta.ACTIVA, EstadoCuenta.BLOQUEADA, adminId));

        // Act + Assert
        mockMvc.perform(post("/api/cuentas/" + CUENTA_ID + "/bloquear")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoNuevo").value("BLOQUEADA"));
        verify(jwtService).esTokenValido(TOKEN);
        verify(estadoCuentaService).bloquear(CUENTA_ID, adminId, request);
    }

    @Test
    @DisplayName("Un ADMIN puede desbloquear y el responsable se toma del token")
    void adminPuedeDesbloquear() throws Exception {
        // Arrange
        UUID adminId = UUID.randomUUID();
        CambioEstadoCuentaRequest request = new CambioEstadoCuentaRequest(MOTIVO);
        autenticarComo(adminId, "ADMIN");
        when(estadoCuentaService.desbloquear(CUENTA_ID, adminId, request))
                .thenReturn(respuesta(TipoAccionCuenta.DESBLOQUEO, EstadoCuenta.BLOQUEADA, EstadoCuenta.ACTIVA, adminId));

        // Act + Assert
        mockMvc.perform(post("/api/cuentas/" + CUENTA_ID + "/desbloquear")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoNuevo").value("ACTIVA"));
        verify(jwtService).esTokenValido(TOKEN);
        verify(estadoCuentaService).desbloquear(CUENTA_ID, adminId, request);
    }

    @Test
    @DisplayName("Un ADMIN puede cerrar y el responsable se toma del token")
    void adminPuedeCerrar() throws Exception {
        // Arrange
        UUID adminId = UUID.randomUUID();
        CambioEstadoCuentaRequest request = new CambioEstadoCuentaRequest(MOTIVO);
        autenticarComo(adminId, "ADMIN");
        when(estadoCuentaService.cerrar(CUENTA_ID, adminId, request))
                .thenReturn(respuesta(TipoAccionCuenta.CIERRE, EstadoCuenta.ACTIVA, EstadoCuenta.CERRADA, adminId));

        // Act + Assert
        mockMvc.perform(post("/api/cuentas/" + CUENTA_ID + "/cerrar")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoNuevo").value("CERRADA"));
        verify(jwtService).esTokenValido(TOKEN);
        verify(estadoCuentaService).cerrar(CUENTA_ID, adminId, request);
    }

    private void autenticarComo(UUID usuarioId, String rol) {
        when(jwtService.esTokenValido(TOKEN)).thenReturn(true);
        when(jwtService.extraerUsuarioId(TOKEN)).thenReturn(usuarioId);
        when(jwtService.extraerRol(TOKEN)).thenReturn(rol);
        when(jwtService.extraerClienteId(TOKEN)).thenReturn(null);
    }

    private CambioEstadoCuentaResponse respuesta(TipoAccionCuenta accion, EstadoCuenta anterior,
                                                EstadoCuenta nuevo, UUID usuarioId) {
        return new CambioEstadoCuentaResponse("1234567890", accion, anterior, nuevo,
                MOTIVO, LocalDateTime.now(), usuarioId);
    }
}
