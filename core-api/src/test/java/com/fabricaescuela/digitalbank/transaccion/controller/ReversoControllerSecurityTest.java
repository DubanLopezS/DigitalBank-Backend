package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.core.config.SecurityConfig;
import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
import com.fabricaescuela.digitalbank.core.web.ApiAccessDeniedHandler;
import com.fabricaescuela.digitalbank.core.web.ApiAuthenticationEntryPoint;
import com.fabricaescuela.digitalbank.core.web.ErrorResponseWriter;
import com.fabricaescuela.digitalbank.transaccion.dto.ReversoResponse;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IReversoService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReversoController.class)
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ErrorResponseWriter.class})
@DisplayName("ReversoController - autorizacion por rol (HU-12)")
class ReversoControllerSecurityTest {

    private static final UUID TRANSACCION_ID = UUID.randomUUID();
    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final String RUTA = "/api/v1/transacciones/" + TRANSACCION_ID + "/reversos";
    private static final String TOKEN = "token-de-prueba";
    private static final BigDecimal MONTO = new BigDecimal("30000.00");
    private static final String BODY_VALIDO = "{\"motivo\":\"Deposito duplicado por el cajero\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private IReversoService reversoService;

    @Test
    @DisplayName("Sin token responde 401 y el servicio no se invoca")
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("NO_AUTENTICADO"));
        verifyNoInteractions(reversoService);
    }

    @ParameterizedTest(name = "Un {0} recibe 403 al reversar")
    @ValueSource(strings = {"CLIENTE", "CAJERO"})
    @DisplayName("Solo ADMIN puede reversar: CLIENTE y CAJERO reciben 403")
    void rolSinPermisoRecibe403(String rol) throws Exception {
        autenticarComo(rol, UUID.randomUUID());

        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESO_NO_AUTORIZADO"));
        verifyNoInteractions(reversoService);
    }

    @Test
    @DisplayName("Un ADMIN pasa la seguridad y el reverso responde 201")
    void adminReversa201() throws Exception {
        autenticarComo("ADMIN", null);
        ReversoResponse esperada = new ReversoResponse(
                TRANSACCION_ID, "DEPOSITO", "REVERSADA", MONTO, "Deposito duplicado por el cajero",
                UUID.randomUUID(), LocalDateTime.now(),
                List.of(new ReversoResponse.MovimientoReverso(UUID.randomUUID(), CUENTA_ID, "SALIENTE",
                        MONTO, BigDecimal.ZERO)));
        when(reversoService.reversar(eq(TRANSACCION_ID), any(), any(UUID.class))).thenReturn(esperada);

        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoOriginal").value("DEPOSITO"))
                .andExpect(jsonPath("$.estadoOriginal").value("REVERSADA"));
        verify(reversoService).reversar(eq(TRANSACCION_ID), any(), any(UUID.class));
    }

    @Test
    @DisplayName("Un motivo de menos de 10 caracteres responde 400 y el servicio no se invoca")
    void motivoCortoResponde400() throws Exception {
        autenticarComo("ADMIN", null);

        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"corto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("DATOS_INVALIDOS"));
        verifyNoInteractions(reversoService);
    }

    private void autenticarComo(String rol, UUID clienteId) {
        when(jwtService.esTokenValido(TOKEN)).thenReturn(true);
        when(jwtService.extraerUsuarioId(TOKEN)).thenReturn(UUID.randomUUID());
        when(jwtService.extraerRol(TOKEN)).thenReturn(rol);
        when(jwtService.extraerClienteId(TOKEN)).thenReturn(clienteId);
    }
}
