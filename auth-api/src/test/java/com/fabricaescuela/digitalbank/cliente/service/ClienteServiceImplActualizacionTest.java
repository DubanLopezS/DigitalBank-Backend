package com.fabricaescuela.digitalbank.cliente.service;

import com.fabricaescuela.digitalbank.auth.entity.Rol;
import com.fabricaescuela.digitalbank.auth.entity.Usuario;
import com.fabricaescuela.digitalbank.auth.interfaces.repositories.IUsuarioRepository;
import com.fabricaescuela.digitalbank.auth.interfaces.services.ICredencialesService;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteActualizacionRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResponse;
import com.fabricaescuela.digitalbank.cliente.entity.AuditoriaDatosCliente;
import com.fabricaescuela.digitalbank.cliente.entity.CampoAuditado;
import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cliente.exception.CorreoYaRegistradoException;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.IAuditoriaDatosClienteRepository;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.IClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("ClienteServiceImpl - actualizacion de datos de contacto")
class ClienteServiceImplActualizacionTest {

    private static final String DOCUMENTO = "1020304050";
    private static final String EMAIL_ACTUAL = "ana@banco.com";
    private static final String EMAIL_NUEVO = "ana.gomez@banco.com";
    private static final String TELEFONO_ACTUAL = "3001234567";
    private static final String TELEFONO_NUEVO = "3109876543";

    @Mock
    private IClienteRepository clienteRepository;

    @Mock
    private ICredencialesService credencialesService;

    @Mock
    private IUsuarioRepository usuarioRepository;

    @Mock
    private IAuditoriaDatosClienteRepository auditoriaRepository;

    @InjectMocks
    private ClienteServiceImpl clienteService;

    private UUID clienteId;
    private UUID adminId;
    private Cliente cliente;
    private Usuario usuarioDelCliente;

    @BeforeEach
    void prepararCliente() {
        clienteId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        cliente = new Cliente(TipoDocumento.CC, DOCUMENTO, "Ana", "Gomez",
                LocalDate.now().minusYears(30), TELEFONO_ACTUAL, EMAIL_ACTUAL);
        ReflectionTestUtils.setField(cliente, "id", clienteId);
        usuarioDelCliente = new Usuario(clienteId, EMAIL_ACTUAL, "hash", Rol.CLIENTE);
    }

    @Test
    @DisplayName("Cambiar el correo actualiza el cliente y su usuario y deja una fila de auditoria")
    void cambioDeCorreoActualizaClienteUsuarioYAuditoria() {
        // Arrange
        prepararGuardadoDeCliente();
        when(usuarioRepository.findByEmail(EMAIL_NUEVO)).thenReturn(Optional.empty());
        when(clienteRepository.existsByEmail(EMAIL_NUEVO)).thenReturn(false);
        when(usuarioRepository.findByClienteId(clienteId)).thenReturn(Optional.of(usuarioDelCliente));

        // Act
        ClienteResponse response = clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest(EMAIL_NUEVO, null), adminId);

        // Assert
        assertThat(response.email()).isEqualTo(EMAIL_NUEVO);
        assertThat(response.telefono()).isEqualTo(TELEFONO_ACTUAL);
        assertThat(cliente.getEmail()).isEqualTo(EMAIL_NUEVO);
        assertThat(usuarioDelCliente.getEmail()).isEqualTo(EMAIL_NUEVO);
        verify(usuarioRepository).save(usuarioDelCliente);

        List<AuditoriaDatosCliente> filas = filasDeAuditoria(1);
        AuditoriaDatosCliente fila = filas.get(0);
        assertThat(fila.getClienteId()).isEqualTo(clienteId);
        assertThat(fila.getUsuarioId()).isEqualTo(adminId);
        assertThat(fila.getCampo()).isEqualTo(CampoAuditado.EMAIL);
        assertThat(fila.getValorAnterior()).isEqualTo(EMAIL_ACTUAL);
        assertThat(fila.getValorNuevo()).isEqualTo(EMAIL_NUEVO);
        assertThat(fila.getFechaHora()).isNotNull();
    }

    @Test
    @DisplayName("Cambiar el telefono deja una fila de auditoria y no toca el usuario")
    void cambioDeTelefonoDejaUnaFila() {
        // Arrange
        prepararGuardadoDeCliente();

        // Act
        ClienteResponse response = clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest(null, TELEFONO_NUEVO), adminId);

        // Assert
        assertThat(response.telefono()).isEqualTo(TELEFONO_NUEVO);
        assertThat(response.email()).isEqualTo(EMAIL_ACTUAL);
        AuditoriaDatosCliente fila = filasDeAuditoria(1).get(0);
        assertThat(fila.getCampo()).isEqualTo(CampoAuditado.TELEFONO);
        assertThat(fila.getValorAnterior()).isEqualTo(TELEFONO_ACTUAL);
        assertThat(fila.getValorNuevo()).isEqualTo(TELEFONO_NUEVO);
        assertThat(fila.getUsuarioId()).isEqualTo(adminId);
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("Cambiar correo y telefono deja dos filas de auditoria")
    void cambioDeAmbosDejaDosFilas() {
        // Arrange
        prepararGuardadoDeCliente();
        when(usuarioRepository.findByEmail(EMAIL_NUEVO)).thenReturn(Optional.empty());
        when(clienteRepository.existsByEmail(EMAIL_NUEVO)).thenReturn(false);
        when(usuarioRepository.findByClienteId(clienteId)).thenReturn(Optional.of(usuarioDelCliente));

        // Act
        clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest(EMAIL_NUEVO, TELEFONO_NUEVO), adminId);

        // Assert
        assertThat(filasDeAuditoria(2))
                .extracting(AuditoriaDatosCliente::getCampo)
                .containsExactly(CampoAuditado.EMAIL, CampoAuditado.TELEFONO);
    }

    @Test
    @DisplayName("Enviar el mismo correo actual con otras mayusculas no es error y no deja filas")
    void mismoCorreoConOtrasMayusculasNoEsCambio() {
        // Arrange
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(cliente));

        // Act
        ClienteResponse response = clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest("  ANA@Banco.com ", null), adminId);

        // Assert
        assertThat(response.email()).isEqualTo(EMAIL_ACTUAL);
        verify(clienteRepository, never()).existsByEmail(any());
        verify(clienteRepository, never()).save(any());
        verifyNoInteractions(usuarioRepository, auditoriaRepository);
    }

    @Test
    @DisplayName("Enviar el mismo correo y el mismo telefono actuales responde el cliente sin dejar filas")
    void mismosDatosNoDejanFilas(CapturedOutput salida) {
        // Arrange
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(cliente));

        // Act
        ClienteResponse response = clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest(EMAIL_ACTUAL, TELEFONO_ACTUAL), adminId);

        // Assert
        assertThat(response.id()).isEqualTo(clienteId);
        verify(clienteRepository, never()).save(any());
        verifyNoInteractions(usuarioRepository, auditoriaRepository);
        assertThat(salida.getAll()).doesNotContain("evento=CLIENTE_ACTUALIZADO");
    }

    @Test
    @DisplayName("Un correo de otro cliente escrito con otras mayusculas responde 409 y no guarda nada")
    void correoDeOtroClienteResponde409() {
        // Arrange
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.findByEmail("otro@banco.com"))
                .thenReturn(Optional.of(new Usuario(UUID.randomUUID(), "otro@banco.com", "hash", Rol.CLIENTE)));
        when(clienteRepository.existsByEmail("otro@banco.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest("Otro@BANCO.com", null), adminId))
                .isInstanceOf(CorreoYaRegistradoException.class)
                .hasMessage("El correo ya está registrado")
                .satisfies(ex -> {
                    CorreoYaRegistradoException error = (CorreoYaRegistradoException) ex;
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getErrorCode()).isEqualTo("CORREO_YA_REGISTRADO");
                });

        assertThat(cliente.getEmail()).isEqualTo(EMAIL_ACTUAL);
        verify(clienteRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(auditoriaRepository);
    }

    @Test
    @DisplayName("Un correo de un usuario interno sin cliente responde 409 y no guarda nada")
    void correoDeUsuarioInternoResponde409() {
        // Arrange
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.findByEmail("cajero@banco.com"))
                .thenReturn(Optional.of(new Usuario(null, "cajero@banco.com", "hash", Rol.CAJERO)));

        // Act & Assert
        assertThatThrownBy(() -> clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest("cajero@banco.com", TELEFONO_NUEVO), adminId))
                .isInstanceOf(CorreoYaRegistradoException.class)
                .extracting(ex -> ((CorreoYaRegistradoException) ex).getErrorCode())
                .isEqualTo("CORREO_YA_REGISTRADO");

        verify(clienteRepository, never()).save(any());
        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(auditoriaRepository);
    }

    @Test
    @DisplayName("Si el usuario del propio cliente ya tiene el correo nuevo no es conflicto")
    void correoDelPropioUsuarioNoEsConflicto() {
        // Arrange
        prepararGuardadoDeCliente();
        when(usuarioRepository.findByEmail(EMAIL_NUEVO))
                .thenReturn(Optional.of(new Usuario(clienteId, EMAIL_NUEVO, "hash", Rol.CLIENTE)));
        when(clienteRepository.existsByEmail(EMAIL_NUEVO)).thenReturn(false);
        when(usuarioRepository.findByClienteId(clienteId)).thenReturn(Optional.of(usuarioDelCliente));

        // Act
        ClienteResponse response = clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest(EMAIL_NUEVO, null), adminId);

        // Assert
        assertThat(response.email()).isEqualTo(EMAIL_NUEVO);
        filasDeAuditoria(1);
    }

    @Test
    @DisplayName("Un cliente inexistente responde 404 y no guarda nada")
    void clienteInexistenteResponde404() {
        // Arrange
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest(EMAIL_NUEVO, TELEFONO_NUEVO), adminId))
                .isInstanceOf(ClienteNoEncontradoException.class)
                .hasMessage("Cliente no encontrado")
                .satisfies(ex -> {
                    ClienteNoEncontradoException error = (ClienteNoEncontradoException) ex;
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(error.getErrorCode()).isEqualTo("CLIENTE_NO_ENCONTRADO");
                });

        verify(clienteRepository, never()).save(any());
        verifyNoInteractions(usuarioRepository, auditoriaRepository);
    }

    @Test
    @DisplayName("El correo con mayusculas y espacios se guarda normalizado en cliente, usuario y auditoria")
    void elCorreoSeGuardaNormalizado() {
        // Arrange
        prepararGuardadoDeCliente();
        when(usuarioRepository.findByEmail(EMAIL_NUEVO)).thenReturn(Optional.empty());
        when(clienteRepository.existsByEmail(EMAIL_NUEVO)).thenReturn(false);
        when(usuarioRepository.findByClienteId(clienteId)).thenReturn(Optional.of(usuarioDelCliente));
        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);

        // Act
        clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest("  Ana.Gomez@BANCO.com ", null), adminId);

        // Assert
        verify(clienteRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo(EMAIL_NUEVO);
        assertThat(usuarioDelCliente.getEmail()).isEqualTo(EMAIL_NUEVO);
        assertThat(filasDeAuditoria(1).get(0).getValorNuevo()).isEqualTo(EMAIL_NUEVO);
    }

    @Test
    @DisplayName("Si el cliente no tiene usuario asociado se actualiza solo el cliente")
    void clienteSinUsuarioActualizaSoloElCliente() {
        // Arrange
        prepararGuardadoDeCliente();
        when(usuarioRepository.findByEmail(EMAIL_NUEVO)).thenReturn(Optional.empty());
        when(clienteRepository.existsByEmail(EMAIL_NUEVO)).thenReturn(false);
        when(usuarioRepository.findByClienteId(clienteId)).thenReturn(Optional.empty());

        // Act
        ClienteResponse response = clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest(EMAIL_NUEVO, null), adminId);

        // Assert
        assertThat(response.email()).isEqualTo(EMAIL_NUEVO);
        verify(usuarioRepository, never()).save(any());
        filasDeAuditoria(1);
    }

    @Test
    @DisplayName("Un cliente sin telefono previo audita el valor anterior como texto vacio")
    void clienteSinTelefonoPrevioAuditaValorVacio() {
        // Arrange
        cliente.cambiarTelefono(null);
        prepararGuardadoDeCliente();

        // Act
        clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest(null, TELEFONO_NUEVO), adminId);

        // Assert
        AuditoriaDatosCliente fila = filasDeAuditoria(1).get(0);
        assertThat(fila.getValorAnterior()).isEmpty();
        assertThat(fila.getValorNuevo()).isEqualTo(TELEFONO_NUEVO);
    }

    @Test
    @DisplayName("La actualizacion no cambia el tipo ni el numero de documento")
    void laActualizacionNoCambiaElDocumento() {
        // Arrange
        prepararGuardadoDeCliente();
        when(usuarioRepository.findByEmail(EMAIL_NUEVO)).thenReturn(Optional.empty());
        when(clienteRepository.existsByEmail(EMAIL_NUEVO)).thenReturn(false);
        when(usuarioRepository.findByClienteId(clienteId)).thenReturn(Optional.of(usuarioDelCliente));

        // Act
        ClienteResponse response = clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest(EMAIL_NUEVO, TELEFONO_NUEVO), adminId);

        // Assert
        assertThat(response.tipoDocumento()).isEqualTo(TipoDocumento.CC);
        assertThat(response.numeroDocumento()).isEqualTo(DOCUMENTO);
        assertThat(response.nombres()).isEqualTo("Ana");
        assertThat(response.apellidos()).isEqualTo("Gomez");
    }

    @Test
    @DisplayName("El log de la actualizacion tiene los ids y los campos, pero no el correo ni el telefono")
    void elLogNoTieneDatosPersonales(CapturedOutput salida) {
        // Arrange
        prepararGuardadoDeCliente();
        when(usuarioRepository.findByEmail(EMAIL_NUEVO)).thenReturn(Optional.empty());
        when(clienteRepository.existsByEmail(EMAIL_NUEVO)).thenReturn(false);
        when(usuarioRepository.findByClienteId(clienteId)).thenReturn(Optional.of(usuarioDelCliente));

        // Act
        clienteService.actualizarCliente(
                clienteId, new ClienteActualizacionRequest(EMAIL_NUEVO, TELEFONO_NUEVO), adminId);

        // Assert
        assertThat(salida.getAll())
                .contains("evento=CLIENTE_ACTUALIZADO clienteId=" + clienteId
                        + " usuarioId=" + adminId + " campos=EMAIL,TELEFONO")
                .doesNotContain(EMAIL_ACTUAL)
                .doesNotContain(EMAIL_NUEVO)
                .doesNotContain(TELEFONO_ACTUAL)
                .doesNotContain(TELEFONO_NUEVO);
    }

    private void prepararGuardadoDeCliente() {
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(cliente));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private List<AuditoriaDatosCliente> filasDeAuditoria(int esperadas) {
        ArgumentCaptor<AuditoriaDatosCliente> captor = ArgumentCaptor.forClass(AuditoriaDatosCliente.class);
        verify(auditoriaRepository, times(esperadas)).save(captor.capture());
        return captor.getAllValues();
    }
}
