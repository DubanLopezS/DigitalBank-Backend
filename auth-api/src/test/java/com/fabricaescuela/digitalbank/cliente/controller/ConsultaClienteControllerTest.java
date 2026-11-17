package com.fabricaescuela.digitalbank.cliente.controller;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteDetalleResponse;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IConsultaClienteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultaClienteController - consulta por tipo y número de documento")
class ConsultaClienteControllerTest {

    private static final String DOCUMENTO = "1020304050";

    @Mock
    private IConsultaClienteService consultaClienteService;

    @InjectMocks
    private ConsultaClienteController controller;

    @Test
    @DisplayName("Un CLIENTE pasa su rol y su clienteId al servicio y recibe 200")
    void clientePasaRolYClienteId() {
        // Arrange
        UUID clienteId = UUID.randomUUID();
        var authentication = autenticacion("CLIENTE", clienteId);
        ClienteDetalleResponse esperada = respuesta(clienteId);
        when(consultaClienteService.consultar(TipoDocumento.CC, DOCUMENTO, clienteId, "CLIENTE"))
                .thenReturn(esperada);

        // Act
        ResponseEntity<ClienteDetalleResponse> respuesta = controller.consultar(TipoDocumento.CC, DOCUMENTO, authentication);

        // Assert
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(esperada);
    }

    @Test
    @DisplayName("Un ADMIN sin clienteId pasa null y su rol al servicio")
    void adminPasaClienteIdNulo() {
        // Arrange
        var authentication = autenticacion("ADMIN", null);
        ClienteDetalleResponse esperada = respuesta(UUID.randomUUID());
        when(consultaClienteService.consultar(TipoDocumento.CC, DOCUMENTO, null, "ADMIN"))
                .thenReturn(esperada);

        // Act
        ResponseEntity<ClienteDetalleResponse> respuesta = controller.consultar(TipoDocumento.CC, DOCUMENTO, authentication);

        // Assert
        assertThat(respuesta.getBody()).isEqualTo(esperada);
        verify(consultaClienteService).consultar(TipoDocumento.CC, DOCUMENTO, null, "ADMIN");
    }

    @Test
    @DisplayName("El controlador propaga el 404 del servicio")
    void propagaClienteNoEncontrado() {
        // Arrange
        var authentication = autenticacion("CAJERO", null);
        when(consultaClienteService.consultar(TipoDocumento.CC, DOCUMENTO, null, "CAJERO"))
                .thenThrow(new ClienteNoEncontradoException("Cliente no encontrado"));

        // Act & Assert
        assertThatThrownBy(() -> controller.consultar(TipoDocumento.CC, DOCUMENTO, authentication))
                .isInstanceOf(ClienteNoEncontradoException.class);
    }

    private UsernamePasswordAuthenticationToken autenticacion(String rol, UUID clienteId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(), null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
        authentication.setDetails(clienteId);
        return authentication;
    }

    private ClienteDetalleResponse respuesta(UUID clienteId) {
        return new ClienteDetalleResponse(clienteId, TipoDocumento.CC, DOCUMENTO, "Ana", "Gomez",
                LocalDate.of(1990, 1, 1), "3001234567", "ana@banco.com", "ACTIVO",
                LocalDateTime.now(), List.of());
    }
}