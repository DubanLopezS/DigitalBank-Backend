package com.fabricaescuela.digitalbank.cliente.service;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteDetalleResponse;
import com.fabricaescuela.digitalbank.cliente.dto.CuentaDelClienteResponse;
import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cliente.exception.ParametroInvalidoException;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.IClienteRepository;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.ICuentasDelClienteRepository;
import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultaClienteServiceImpl - consulta de información del cliente")
class ConsultaClienteServiceImplTest {

    private static final String DOCUMENTO = "1020304050";

    @Mock
    private IClienteRepository clienteRepository;

    @Mock
    private ICuentasDelClienteRepository cuentasRepository;

    @InjectMocks
    private ConsultaClienteServiceImpl servicio;

    @Test
    @DisplayName("ADMIN consulta un cliente existente y recibe sus datos con la lista de cuentas")
    void adminConsultaClienteExistente() {
        // Arrange
        Cliente cliente = cliente(UUID.randomUUID());
        List<CuentaDelClienteResponse> cuentas = List.of(
                new CuentaDelClienteResponse("1234567890", "AHORROS", "ACTIVA"),
                new CuentaDelClienteResponse("0987654321", "CORRIENTE", "CERRADA"));
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.of(cliente));
        when(cuentasRepository.findByClienteId(cliente.getId())).thenReturn(cuentas);

        // Act
        ClienteDetalleResponse respuesta = servicio.consultar(TipoDocumento.CC, DOCUMENTO, null, "ADMIN");

        // Assert
        assertThat(respuesta.clienteId()).isEqualTo(cliente.getId());
        assertThat(respuesta.numeroDocumento()).isEqualTo(DOCUMENTO);
        assertThat(respuesta.email()).isEqualTo("ana@banco.com");
        assertThat(respuesta.estado()).isEqualTo("ACTIVO");
        assertThat(respuesta.cuentas()).isEqualTo(cuentas);
    }

    @Test
    @DisplayName("CAJERO consulta igual que ADMIN")
    void cajeroConsultaClienteExistente() {
        // Arrange
        Cliente cliente = cliente(UUID.randomUUID());
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.of(cliente));
        when(cuentasRepository.findByClienteId(cliente.getId())).thenReturn(List.of());

        // Act
        ClienteDetalleResponse respuesta = servicio.consultar(TipoDocumento.CC, DOCUMENTO, null, "CAJERO");

        // Assert
        assertThat(respuesta.clienteId()).isEqualTo(cliente.getId());
        assertThat(respuesta.cuentas()).isEmpty();
    }

    @Test
    @DisplayName("ADMIN consulta un documento que no existe y recibe 404")
    void adminConsultaClienteInexistente() {
        // Arrange
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> servicio.consultar(TipoDocumento.CC, DOCUMENTO, null, "ADMIN"))
                .isInstanceOf(ClienteNoEncontradoException.class);
        verifyNoInteractions(cuentasRepository);
    }

    @Test
    @DisplayName("CLIENTE consulta su propio documento")
    void clienteConsultaSuPropioDocumento() {
        // Arrange
        UUID clienteId = UUID.randomUUID();
        Cliente cliente = cliente(clienteId);
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.of(cliente));
        when(cuentasRepository.findByClienteId(clienteId)).thenReturn(List.of());

        // Act
        ClienteDetalleResponse respuesta = servicio.consultar(TipoDocumento.CC, DOCUMENTO, clienteId, "CLIENTE");

        // Assert
        assertThat(respuesta.clienteId()).isEqualTo(clienteId);
    }

    @Test
    @DisplayName("CLIENTE consulta el documento de otro cliente y recibe 403")
    void clienteConsultaDocumentoAjeno() {
        // Arrange
        Cliente otroCliente = cliente(UUID.randomUUID());
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.of(otroCliente));

        // Act & Assert
        assertThatThrownBy(() -> servicio.consultar(TipoDocumento.CC, DOCUMENTO, UUID.randomUUID(), "CLIENTE"))
                .isInstanceOf(AccesoNoAutorizadoException.class);
        verifyNoInteractions(cuentasRepository);
    }

    @Test
    @DisplayName("CLIENTE consulta un documento inexistente y recibe el mismo 403")
    void clienteConsultaDocumentoInexistente() {
        // Arrange
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> servicio.consultar(TipoDocumento.CC, DOCUMENTO, UUID.randomUUID(), "CLIENTE"))
                .isInstanceOf(AccesoNoAutorizadoException.class);
        verifyNoInteractions(cuentasRepository);
    }

    @Test
    @DisplayName("CLIENTE sin clienteId en el token recibe 403")
    void clienteSinClienteIdEnElToken() {
        // Arrange
        Cliente cliente = cliente(UUID.randomUUID());
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.of(cliente));

        // Act & Assert
        assertThatThrownBy(() -> servicio.consultar(TipoDocumento.CC, DOCUMENTO, null, "CLIENTE"))
                .isInstanceOf(AccesoNoAutorizadoException.class);
    }

    @Test
    @DisplayName("El número de documento se busca sin espacios al inicio ni al final")
    void documentoSeBuscaConTrim() {
        // Arrange
        Cliente cliente = cliente(UUID.randomUUID());
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.of(cliente));
        when(cuentasRepository.findByClienteId(cliente.getId())).thenReturn(List.of());

        // Act
        servicio.consultar(TipoDocumento.CC, "  " + DOCUMENTO + "  ", null, "ADMIN");

        // Assert
        verify(clienteRepository).findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO);
    }

    @Test
    @DisplayName("Un número de documento vacío o solo con espacios responde 400")
    void documentoVacioResponde400() {
        // Act & Assert
        assertThatThrownBy(() -> servicio.consultar(TipoDocumento.CC, "   ", null, "ADMIN"))
                .isInstanceOf(ParametroInvalidoException.class);
        verifyNoInteractions(clienteRepository, cuentasRepository);
    }

    @Test
    @DisplayName("Un número de documento de más de 30 caracteres responde 400")
    void documentoMuyLargoResponde400() {
        // Arrange
        String documentoLargo = "1".repeat(31);

        // Act & Assert
        assertThatThrownBy(() -> servicio.consultar(TipoDocumento.CC, documentoLargo, null, "ADMIN"))
                .isInstanceOf(ParametroInvalidoException.class);
        verifyNoInteractions(clienteRepository, cuentasRepository);
    }

    @Test
    @DisplayName("El 400 se valida antes que el 403: un CLIENTE con documento vacío recibe 400")
    void documentoInvalidoGanaAlAcceso() {
        // Act & Assert
        assertThatThrownBy(() -> servicio.consultar(TipoDocumento.CC, "", UUID.randomUUID(), "CLIENTE"))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    private Cliente cliente(UUID id) {
        Cliente cliente = new Cliente(TipoDocumento.CC, DOCUMENTO, "Ana", "Gomez",
                LocalDate.of(1990, 1, 1), "3001234567", "ana@banco.com");
        ReflectionTestUtils.setField(cliente, "id", id);
        return cliente;
    }
}