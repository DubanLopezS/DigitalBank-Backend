package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.core.config.SecurityConfig;
import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
import com.fabricaescuela.digitalbank.transaccion.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.entity.OrigenDeposito;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IDepositoService;
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

@WebMvcTest(DepositoController.class)
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@DisplayName("DepositoController - autorizacion por rol")
class DepositoControllerSecurityTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final String RUTA = "/api/v1/cuentas/" + CUENTA_ID + "/depositos";
    private static final String TOKEN = "token-de-prueba";
    private static final BigDecimal MONTO = new BigDecimal("50000.25");
    private static final String BODY_VALIDO = "{\"monto\":50000.25,\"origen\":\"EFECTIVO\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private IDepositoService depositoService;

    @Test
    @DisplayName("Sin token responde 401 y el servicio no se invoca")
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(depositoService);
    }

    @Test
    @DisplayName("Un CLIENTE recibe 403 al depositar y el servicio no se invoca")
    void clienteRecibe403() throws Exception {
        // Arrange
        autenticarComo("CLIENTE", UUID.randomUUID());

        // Act + Assert
        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(depositoService);
    }

    @ParameterizedTest(name = "Un {0} puede depositar y responde 201")
    @ValueSource(strings = {"ADMIN", "CAJERO"})
    @DisplayName("ADMIN y CAJERO pasan la seguridad y el deposito responde 201")
    void personalAutorizadoDeposita201(String rol) throws Exception {
        // Arrange
        DepositoRequest request = new DepositoRequest(MONTO, OrigenDeposito.EFECTIVO);
        autenticarComo(rol, null);
        when(depositoService.registrarDeposito(CUENTA_ID, request)).thenReturn(new TransaccionResponse(
                UUID.randomUUID(), CUENTA_ID, "DEPOSITO", MONTO, BigDecimal.ZERO, MONTO,
                "EFECTIVO", "COMPLETADA", LocalDateTime.now()));

        // Act + Assert
        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DEPOSITO"));
        verify(depositoService).registrarDeposito(CUENTA_ID, request);
    }

    @Test
    @DisplayName("Un monto con tres decimales responde 400 Monto invalido y el servicio no se invoca")
    void montoConTresDecimalesResponde400() throws Exception {
        // Arrange
        autenticarComo("CAJERO", null);

        // Act + Assert
        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":0.004,\"origen\":\"EFECTIVO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Monto inválido"));
        verifyNoInteractions(depositoService);
    }

    private void autenticarComo(String rol, UUID clienteId) {
        when(jwtService.esTokenValido(TOKEN)).thenReturn(true);
        when(jwtService.extraerUsuarioId(TOKEN)).thenReturn(UUID.randomUUID());
        when(jwtService.extraerRol(TOKEN)).thenReturn(rol);
        when(jwtService.extraerClienteId(TOKEN)).thenReturn(clienteId);
    }
}
