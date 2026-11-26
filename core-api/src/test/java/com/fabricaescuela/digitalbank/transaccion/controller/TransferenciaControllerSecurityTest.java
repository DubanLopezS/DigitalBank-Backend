package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.core.config.SecurityConfig;
import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
import com.fabricaescuela.digitalbank.core.web.ApiAccessDeniedHandler;
import com.fabricaescuela.digitalbank.core.web.ApiAuthenticationEntryPoint;
import com.fabricaescuela.digitalbank.core.web.ErrorResponseWriter;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaResponse;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.ITransferenciaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferenciaController.class)
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ErrorResponseWriter.class})
@DisplayName("TransferenciaController - autorizacion por rol")
class TransferenciaControllerSecurityTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final String RUTA = "/api/v1/cuentas/" + CUENTA_ID + "/transferencias";
    private static final String TOKEN = "token-de-prueba";
    private static final String CUENTA_DESTINO = "9876543210";
    private static final BigDecimal MONTO = new BigDecimal("50000.25");
    private static final String DESCRIPCION = "Pago del arriendo";
    private static final String BODY_VALIDO =
            "{\"cuentaDestino\":\"9876543210\",\"monto\":50000.25,\"descripcion\":\"Pago del arriendo\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private ITransferenciaService transferenciaService;

    @Test
    @DisplayName("Sin token responde 401 y el servicio no se invoca")
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(transferenciaService);
    }

    @ParameterizedTest(name = "Un {0} recibe 403 al transferir")
    @ValueSource(strings = {"ADMIN", "CAJERO"})
    @DisplayName("Solo un CLIENTE puede transferir: ADMIN y CAJERO reciben 403 y el servicio no se invoca")
    void personalInternoRecibe403(String rol) throws Exception {
        // Arrange
        autenticarComo(rol, null);

        // Act + Assert
        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESO_NO_AUTORIZADO"))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(transferenciaService);
    }

    @Test
    @DisplayName("Un CLIENTE pasa la seguridad, la transferencia responde 201 y lleva el cliente y el usuario del token")
    void clienteTransfiere201() throws Exception {
        // Arrange
        UUID usuarioId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        TransferenciaRequest request = new TransferenciaRequest(CUENTA_DESTINO, MONTO, DESCRIPCION);
        autenticarComo(usuarioId, "CLIENTE", clienteId);
        when(transferenciaService.registrarTransferencia(CUENTA_ID, request, clienteId, usuarioId)).thenReturn(
                new TransferenciaResponse(UUID.randomUUID(), UUID.randomUUID(), CUENTA_ID, CUENTA_DESTINO,
                        MONTO, DESCRIPCION, "COMPLETADA", LocalDateTime.now()));

        // Act + Assert
        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cuentaDestino").value(CUENTA_DESTINO));
        verify(transferenciaService).registrarTransferencia(CUENTA_ID, request, clienteId, usuarioId);
    }

    private void autenticarComo(String rol, UUID clienteId) {
        autenticarComo(UUID.randomUUID(), rol, clienteId);
    }

    private void autenticarComo(UUID usuarioId, String rol, UUID clienteId) {
        when(jwtService.esTokenValido(TOKEN)).thenReturn(true);
        when(jwtService.extraerUsuarioId(TOKEN)).thenReturn(usuarioId);
        when(jwtService.extraerRol(TOKEN)).thenReturn(rol);
        when(jwtService.extraerClienteId(TOKEN)).thenReturn(clienteId);
    }
}
