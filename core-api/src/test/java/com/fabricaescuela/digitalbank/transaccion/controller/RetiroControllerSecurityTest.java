package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.core.config.SecurityConfig;
import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
import com.fabricaescuela.digitalbank.core.web.ApiAccessDeniedHandler;
import com.fabricaescuela.digitalbank.core.web.ApiAuthenticationEntryPoint;
import com.fabricaescuela.digitalbank.core.web.ErrorResponseWriter;
import com.fabricaescuela.digitalbank.transaccion.dto.RetiroRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IRetiroService;
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

@WebMvcTest(RetiroController.class)
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ErrorResponseWriter.class})
@DisplayName("RetiroController - autorizacion por rol")
class RetiroControllerSecurityTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final String RUTA = "/api/v1/cuentas/" + CUENTA_ID + "/retiros";
    private static final String TOKEN = "token-de-prueba";
    private static final BigDecimal MONTO = new BigDecimal("50000.25");
    private static final String BODY_VALIDO = "{\"monto\":50000.25}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private IRetiroService retiroService;

    @Test
    @DisplayName("Sin token responde 401 y el servicio no se invoca")
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(retiroService);
    }

    @ParameterizedTest(name = "Un {0} recibe 403 al retirar")
    @ValueSource(strings = {"ADMIN", "CAJERO"})
    @DisplayName("Solo un CLIENTE puede retirar: ADMIN y CAJERO reciben 403 y el servicio no se invoca")
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
        verifyNoInteractions(retiroService);
    }

    @Test
    @DisplayName("Un CLIENTE pasa la seguridad, el retiro responde 201 y lleva el cliente y el usuario del token")
    void clienteRetira201() throws Exception {
        // Arrange
        UUID usuarioId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        RetiroRequest request = new RetiroRequest(MONTO);
        autenticarComo(usuarioId, "CLIENTE", clienteId);
        when(retiroService.registrarRetiro(CUENTA_ID, request, clienteId, usuarioId)).thenReturn(
                new TransaccionResponse(UUID.randomUUID(), CUENTA_ID, "RETIRO", MONTO, MONTO, BigDecimal.ZERO,
                        null, "COMPLETADA", LocalDateTime.now()));

        // Act + Assert
        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("RETIRO"));
        verify(retiroService).registrarRetiro(CUENTA_ID, request, clienteId, usuarioId);
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
