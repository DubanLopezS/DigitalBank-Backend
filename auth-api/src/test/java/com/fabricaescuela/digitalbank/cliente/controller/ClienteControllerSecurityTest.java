package com.fabricaescuela.digitalbank.cliente.controller;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteRegistroRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResponse;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteService;
import com.fabricaescuela.digitalbank.core.config.SecurityConfig;
import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClienteController.class)
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@DisplayName("ClienteController - autorizacion por rol")
class ClienteControllerSecurityTest {

    private static final String RUTA = "/api/clientes";
    private static final String TOKEN = "token-de-prueba";
    private static final String BODY_VALIDO = """
            {
              "tipoDocumento": "CC",
              "numeroDocumento": "1020304050",
              "nombres": "Ana",
              "apellidos": "Gomez",
              "fechaNacimiento": "1995-05-10",
              "telefono": "3001234567",
              "email": "ana@banco.com",
              "password": "Password123"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private IClienteService clienteService;

    @Test
    @DisplayName("Sin token responde 401 y el servicio no se invoca")
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(clienteService);
    }

    @Test
    @DisplayName("Un CLIENTE recibe 403 al registrar clientes y el servicio no se invoca")
    void clienteRecibe403() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "CLIENTE");

        // Act + Assert
        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(clienteService);
    }

    @Test
    @DisplayName("Un CAJERO recibe 403 al registrar clientes y el servicio no se invoca")
    void cajeroRecibe403() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "CAJERO");

        // Act + Assert
        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(clienteService);
    }

    @Test
    @DisplayName("Un ADMIN pasa la seguridad y el registro responde 201")
    void adminRegistraCliente201() throws Exception {
        // Arrange
        UUID clienteId = UUID.randomUUID();
        autenticarComo(UUID.randomUUID(), "ADMIN");
        when(clienteService.registrarCliente(any(ClienteRegistroRequest.class))).thenReturn(new ClienteResponse(
                clienteId, TipoDocumento.CC, "1020304050", "Ana", "Gomez",
                LocalDate.of(1995, 5, 10), "3001234567", "ana@banco.com", "ACTIVO", LocalDateTime.now()));

        // Act + Assert
        mockMvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(clienteId.toString()));
        verify(jwtService).esTokenValido(TOKEN);
        verify(clienteService).registrarCliente(any(ClienteRegistroRequest.class));
    }

    private void autenticarComo(UUID usuarioId, String rol) {
        when(jwtService.esTokenValido(TOKEN)).thenReturn(true);
        when(jwtService.extraerUsuarioId(TOKEN)).thenReturn(usuarioId);
        when(jwtService.extraerRol(TOKEN)).thenReturn(rol);
    }
}
