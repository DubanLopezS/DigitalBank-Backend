package com.fabricaescuela.digitalbank.cliente.controller;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteActualizacionRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResponse;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cliente.exception.CorreoYaRegistradoException;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteService;
import com.fabricaescuela.digitalbank.core.config.SecurityConfig;
import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
import com.fabricaescuela.digitalbank.core.web.ApiAccessDeniedHandler;
import com.fabricaescuela.digitalbank.core.web.ApiAuthenticationEntryPoint;
import com.fabricaescuela.digitalbank.core.web.ErrorResponseWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClienteController.class)
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ErrorResponseWriter.class})
@DisplayName("ClienteController - actualizacion de datos de contacto")
class ClienteControllerActualizacionSecurityTest {

    private static final UUID CLIENTE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String RUTA = "/api/v1/clientes/" + CLIENTE_ID;
    private static final String TOKEN = "token-de-prueba";
    private static final String BODY_VALIDO = """
            {
                "email": "ana.gomez@banco.com",
                "telefono": "3109876543"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private IClienteService clienteService;

    @Test
    @DisplayName("Sin token responde 401 NO_AUTENTICADO y el servicio no se invoca")
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(patch(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(clienteService);
    }

    @Test
    @DisplayName("Un token invalido responde 401 NO_AUTENTICADO y el servicio no se invoca")
    void tokenInvalidoResponde401() throws Exception {
        // Arrange
        when(jwtService.esTokenValido(TOKEN)).thenReturn(false);

        // Act + Assert
        mockMvc.perform(patch(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NO_AUTENTICADO"));
        verifyNoInteractions(clienteService);
    }

    @Test
    @DisplayName("Un CLIENTE recibe 403 ACCESO_NO_AUTORIZADO y el servicio no se invoca")
    void clienteRecibe403() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "CLIENTE");

        // Act + Assert
        mockMvc.perform(patch(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_NO_AUTORIZADO"))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(clienteService);
    }

    @Test
    @DisplayName("Un CAJERO recibe 403 ACCESO_NO_AUTORIZADO y el servicio no se invoca")
    void cajeroRecibe403() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "CAJERO");

        // Act + Assert
        mockMvc.perform(patch(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_NO_AUTORIZADO"))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(clienteService);
    }

    @Test
    @DisplayName("Un ADMIN actualiza y recibe 200 con el cliente; el servicio recibe su usuarioId")
    void adminActualizaCliente200() throws Exception {
        // Arrange
        UUID adminId = UUID.randomUUID();
        autenticarComo(adminId, "ADMIN");
        when(clienteService.actualizarCliente(eq(CLIENTE_ID), any(ClienteActualizacionRequest.class), eq(adminId)))
                .thenReturn(respuesta("ana.gomez@banco.com", "3109876543"));

        // Act + Assert
        mockMvc.perform(patch(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CLIENTE_ID.toString()))
                .andExpect(jsonPath("$.email").value("ana.gomez@banco.com"))
                .andExpect(jsonPath("$.telefono").value("3109876543"))
                .andExpect(jsonPath("$.tipoDocumento").value("CC"))
                .andExpect(jsonPath("$.numeroDocumento").value("1020304050"));
        verify(clienteService).actualizarCliente(
                CLIENTE_ID, new ClienteActualizacionRequest("ana.gomez@banco.com", "3109876543"), adminId);
    }

    @Test
    @DisplayName("Un JSON con tipoDocumento y numeroDocumento no los pasa al servicio ni los cambia")
    void documentoEnElJsonSeIgnora() throws Exception {
        // Arrange
        UUID adminId = UUID.randomUUID();
        autenticarComo(adminId, "ADMIN");
        when(clienteService.actualizarCliente(eq(CLIENTE_ID), any(ClienteActualizacionRequest.class), eq(adminId)))
                .thenReturn(respuesta("ana@banco.com", "3109876543"));
        String body = """
                {
                    "tipoDocumento": "PASAPORTE",
                    "numeroDocumento": "999999",
                    "telefono": "3109876543"
                }
                """;
        ArgumentCaptor<ClienteActualizacionRequest> captor = ArgumentCaptor.forClass(ClienteActualizacionRequest.class);

        // Act + Assert
        mockMvc.perform(patch(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoDocumento").value("CC"))
                .andExpect(jsonPath("$.numeroDocumento").value("1020304050"));
        verify(clienteService).actualizarCliente(eq(CLIENTE_ID), captor.capture(), eq(adminId));
        assertThat(captor.getValue()).isEqualTo(new ClienteActualizacionRequest(null, "3109876543"));
    }

    @Test
    @DisplayName("Un correo con formato invalido responde 400 DATOS_INVALIDOS con Formato de correo invalido")
    void correoInvalidoResponde400() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "ADMIN");

        // Act + Assert
        mockMvc.perform(patch(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana-banco.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.message").value("Formato de correo inválido"));
        verifyNoInteractions(clienteService);
    }

    @Test
    @DisplayName("Un correo de mas de 150 caracteres responde 400 DATOS_INVALIDOS")
    void correoDemasiadoLargoResponde400() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "ADMIN");
        String email = "a".repeat(60) + "@" + "b".repeat(40) + "." + "c".repeat(45) + ".com";

        // Act + Assert
        mockMvc.perform(patch(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.message").value("El correo no puede superar 150 caracteres"));
        verifyNoInteractions(clienteService);
    }

    @Test
    @DisplayName("Un body sin correo ni telefono responde 400 DATOS_INVALIDOS")
    void bodySinDatosResponde400() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "ADMIN");

        // Act + Assert
        mockMvc.perform(patch(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.message").value("Debe enviar el correo o el teléfono"));
        verifyNoInteractions(clienteService);
    }

    @Test
    @DisplayName("Un cliente inexistente responde 404 CLIENTE_NO_ENCONTRADO")
    void clienteInexistenteResponde404() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "ADMIN");
        when(clienteService.actualizarCliente(eq(CLIENTE_ID), any(ClienteActualizacionRequest.class), any(UUID.class)))
                .thenThrow(new ClienteNoEncontradoException("Cliente no encontrado"));

        // Act + Assert
        mockMvc.perform(patch(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CLIENTE_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.message").value("Cliente no encontrado"));
    }

    @Test
    @DisplayName("Un correo ya registrado responde 409 CORREO_YA_REGISTRADO")
    void correoYaRegistradoResponde409() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "ADMIN");
        when(clienteService.actualizarCliente(eq(CLIENTE_ID), any(ClienteActualizacionRequest.class), any(UUID.class)))
                .thenThrow(new CorreoYaRegistradoException("El correo ya está registrado"));

        // Act + Assert
        mockMvc.perform(patch(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CORREO_YA_REGISTRADO"))
                .andExpect(jsonPath("$.message").value("El correo ya está registrado"));
    }

    private ClienteResponse respuesta(String email, String telefono) {
        return new ClienteResponse(
                CLIENTE_ID, TipoDocumento.CC, "1020304050", "Ana", "Gomez",
                LocalDate.of(1995, 5, 10), telefono, email, "ACTIVO", LocalDateTime.now());
    }

    private void autenticarComo(UUID usuarioId, String rol) {
        when(jwtService.esTokenValido(TOKEN)).thenReturn(true);
        when(jwtService.extraerUsuarioId(TOKEN)).thenReturn(usuarioId);
        when(jwtService.extraerRol(TOKEN)).thenReturn(rol);
    }
}
