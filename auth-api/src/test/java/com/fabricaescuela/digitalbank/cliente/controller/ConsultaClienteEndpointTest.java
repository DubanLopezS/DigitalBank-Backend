package com.fabricaescuela.digitalbank.cliente.controller;

import com.fabricaescuela.digitalbank.cliente.exception.ParametroInvalidoException;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteService;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IConsultaClienteService;
import com.fabricaescuela.digitalbank.core.config.SecurityConfig;
import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
import com.fabricaescuela.digitalbank.core.web.ApiAccessDeniedHandler;
import com.fabricaescuela.digitalbank.core.web.ApiAuthenticationEntryPoint;
import com.fabricaescuela.digitalbank.core.web.ErrorResponseWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsultaClienteController.class)
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ErrorResponseWriter.class})
@DisplayName("GET /api/v1/clientes - errores 400 de parámetros")
class ConsultaClienteEndpointTest {

    private static final String RUTA = "/api/v1/clientes";
    private static final String TOKEN = "Bearer token-de-prueba";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private IClienteService clienteService;

    @MockitoBean
    private IConsultaClienteService consultaClienteService;

    @BeforeEach
    void autenticarComoAdmin() {
        when(jwtService.esTokenValido("token-de-prueba")).thenReturn(true);
        when(jwtService.extraerUsuarioId("token-de-prueba")).thenReturn(UUID.randomUUID());
        when(jwtService.extraerRol("token-de-prueba")).thenReturn("ADMIN");
        when(jwtService.extraerClienteId("token-de-prueba")).thenReturn(null);
    }

    @Test
    @DisplayName("Sin numeroDocumento responde 400 PARAMETRO_REQUERIDO")
    void faltaNumeroDocumento() throws Exception {
        // Act & Assert
        mockMvc.perform(get(RUTA).param("tipoDocumento", "CC").header("Authorization", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_REQUERIDO"));
        verifyNoInteractions(consultaClienteService);
    }

    @Test
    @DisplayName("Sin tipoDocumento responde 400 PARAMETRO_REQUERIDO")
    void faltaTipoDocumento() throws Exception {
        // Act & Assert
        mockMvc.perform(get(RUTA).param("numeroDocumento", "1020304050").header("Authorization", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_REQUERIDO"));
        verifyNoInteractions(consultaClienteService);
    }

    @Test
    @DisplayName("Con tipoDocumento inválido responde 400 PARAMETRO_INVALIDO")
    void tipoDocumentoInvalido() throws Exception {
        // Act & Assert
        mockMvc.perform(get(RUTA)
                        .param("tipoDocumento", "XYZ")
                        .param("numeroDocumento", "1020304050")
                        .header("Authorization", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
        verifyNoInteractions(consultaClienteService);
    }

    @Test
    @DisplayName("Con numeroDocumento inválido responde 400 PARAMETRO_INVALIDO")
    void numeroDocumentoInvalido() throws Exception {
        // Arrange
        when(consultaClienteService.consultar(any(), any(), any(), any()))
                .thenThrow(new ParametroInvalidoException("Parámetro inválido: numeroDocumento"));

        // Act & Assert
        mockMvc.perform(get(RUTA)
                        .param("tipoDocumento", "CC")
                        .param("numeroDocumento", " ")
                        .header("Authorization", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
    }
}