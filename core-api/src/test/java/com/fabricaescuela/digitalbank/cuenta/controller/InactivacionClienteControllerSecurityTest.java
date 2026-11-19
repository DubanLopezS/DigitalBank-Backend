package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.cliente.entity.EstadoCliente;
import com.fabricaescuela.digitalbank.core.config.SecurityConfig;
import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
import com.fabricaescuela.digitalbank.core.web.ApiAccessDeniedHandler;
import com.fabricaescuela.digitalbank.core.web.ApiAuthenticationEntryPoint;
import com.fabricaescuela.digitalbank.core.web.ErrorResponseWriter;
import com.fabricaescuela.digitalbank.cuenta.dto.InactivacionClienteRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.InactivacionClienteResponse;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.IInactivacionClienteService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InactivacionClienteController.class)
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ErrorResponseWriter.class})
@DisplayName("InactivacionClienteController - HU-04 autorizacion y validacion")
class InactivacionClienteControllerSecurityTest {

    private static final UUID CLIENTE_ID = UUID.randomUUID();
    private static final String RUTA = "/api/v1/clientes/" + CLIENTE_ID + "/inactivar";
    private static final String MOTIVO = "Solicitud del cliente por cambio de banco";
    private static final String TOKEN = "token-de-prueba";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private IInactivacionClienteService inactivacionClienteService;

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "CAJERO"})
    @DisplayName("AC-1: un rol distinto de ADMIN recibe 403 ACCESO_NO_AUTORIZADO y el servicio no se invoca")
    void rolDistintoDeAdminRecibe403(String rol) throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), rol);

        // Act + Assert
        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(MOTIVO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESO_NO_AUTORIZADO"))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(inactivacionClienteService);
    }

    @Test
    @DisplayName("Sin token responde 401")
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(MOTIVO)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(inactivacionClienteService);
    }

    @Test
    @DisplayName("Un ADMIN puede inactivar y el responsable se toma del token")
    void adminPuedeInactivar() throws Exception {
        // Arrange
        UUID adminId = UUID.randomUUID();
        InactivacionClienteRequest request = new InactivacionClienteRequest(MOTIVO);
        autenticarComo(adminId, "ADMIN");
        when(inactivacionClienteService.inactivar(CLIENTE_ID, adminId, request))
                .thenReturn(new InactivacionClienteResponse(CLIENTE_ID, EstadoCliente.ACTIVO,
                        EstadoCliente.INACTIVO, List.of("1000000001"), MOTIVO, LocalDateTime.now(), adminId));

        // Act + Assert
        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(MOTIVO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoNuevo").value("INACTIVO"))
                .andExpect(jsonPath("$.cuentasBloqueadas[0]").value("1000000001"))
                .andExpect(jsonPath("$.usuarioResponsableId").value(adminId.toString()));
        verify(inactivacionClienteService).inactivar(CLIENTE_ID, adminId, request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "corto", "   corto   "})
    @DisplayName("AC-7: un motivo vacio o de menos de 10 caracteres responde 400 DATOS_INVALIDOS")
    void motivoInvalidoResponde400(String motivo) throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "ADMIN");

        // Act + Assert
        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(motivo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DATOS_INVALIDOS"));
        verifyNoInteractions(inactivacionClienteService);
    }

    private void autenticarComo(UUID usuarioId, String rol) {
        when(jwtService.esTokenValido(TOKEN)).thenReturn(true);
        when(jwtService.extraerUsuarioId(TOKEN)).thenReturn(usuarioId);
        when(jwtService.extraerRol(TOKEN)).thenReturn(rol);
        when(jwtService.extraerClienteId(TOKEN)).thenReturn(null);
    }

    private String body(String motivo) {
        return "{\"motivo\":\"" + motivo + "\"}";
    }
}
