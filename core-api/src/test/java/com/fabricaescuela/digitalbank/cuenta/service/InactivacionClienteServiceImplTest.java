package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cliente.dto.InactivacionRegistrada;
import com.fabricaescuela.digitalbank.cliente.entity.EstadoCliente;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteEstado;
import com.fabricaescuela.digitalbank.cuenta.dto.InactivacionClienteRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.InactivacionClienteResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.MovimientoAdministrativoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoAccionCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteConSaldosPendientesException;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteYaInactivoException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.IMovimientoAdministrativoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InactivacionClienteServiceImpl - HU-04 inactivacion de cliente")
class InactivacionClienteServiceImplTest {

    private static final UUID CLIENTE_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String MOTIVO = "Solicitud del cliente por cambio de banco";
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 18, 10, 30);

    @Mock
    private IClienteEstado clienteEstado;

    @Mock
    private ICuentaRepository cuentaRepository;

    @Mock
    private IMovimientoAdministrativoRepository movimientoCuentaRepository;

    @InjectMocks
    private InactivacionClienteServiceImpl inactivacionService;

    @Test
    @DisplayName("AC-2 y AC-3: con saldo $0 el cliente pasa a INACTIVO y solo sus cuentas ACTIVAS pasan a BLOQUEADA")
    void inactivarBloqueaSoloLasCuentasActivas() {
        // Arrange
        Cuenta activa = cuenta("1000000001", EstadoCuenta.ACTIVA, BigDecimal.ZERO);
        Cuenta bloqueada = cuenta("1000000002", EstadoCuenta.BLOQUEADA, BigDecimal.ZERO);
        Cuenta cerrada = cuenta("1000000003", EstadoCuenta.CERRADA, BigDecimal.ZERO);
        prepararCliente(EstadoCliente.ACTIVO, activa, bloqueada, cerrada);
        when(clienteEstado.inactivar(CLIENTE_ID, ADMIN_ID, MOTIVO))
                .thenReturn(new InactivacionRegistrada(ADMIN_ID, MOTIVO, FECHA));

        // Act
        InactivacionClienteResponse respuesta = inactivacionService.inactivar(CLIENTE_ID, ADMIN_ID, request());

        // Assert
        assertThat(activa.getEstado()).isEqualTo(EstadoCuenta.BLOQUEADA);
        assertThat(bloqueada.getEstado()).isEqualTo(EstadoCuenta.BLOQUEADA);
        assertThat(cerrada.getEstado()).isEqualTo(EstadoCuenta.CERRADA);
        assertThat(respuesta.cuentasBloqueadas()).containsExactly("1000000001");
        assertThat(respuesta.estadoAnterior()).isEqualTo(EstadoCliente.ACTIVO);
        assertThat(respuesta.estadoNuevo()).isEqualTo(EstadoCliente.INACTIVO);
        verify(cuentaRepository).save(activa);
        verify(cuentaRepository, never()).save(bloqueada);
        verify(cuentaRepository, never()).save(cerrada);
        verify(clienteEstado).inactivar(CLIENTE_ID, ADMIN_ID, MOTIVO);
    }

    @Test
    @DisplayName("AC-7: la respuesta trae fecha, hora, usuario y motivo de la auditoria")
    void laRespuestaTraeLaAuditoria() {
        // Arrange
        prepararCliente(EstadoCliente.ACTIVO, cuenta("1000000001", EstadoCuenta.ACTIVA, BigDecimal.ZERO));
        when(clienteEstado.inactivar(CLIENTE_ID, ADMIN_ID, MOTIVO))
                .thenReturn(new InactivacionRegistrada(ADMIN_ID, MOTIVO, FECHA));

        // Act
        InactivacionClienteResponse respuesta = inactivacionService.inactivar(CLIENTE_ID, ADMIN_ID, request());

        // Assert
        assertThat(respuesta.clienteId()).isEqualTo(CLIENTE_ID);
        assertThat(respuesta.usuarioResponsableId()).isEqualTo(ADMIN_ID);
        assertThat(respuesta.motivo()).isEqualTo(MOTIVO);
        assertThat(respuesta.fechaHora()).isEqualTo(FECHA);
    }

    @Test
    @DisplayName("Cada cuenta que se bloquea queda en la auditoria de cuentas con el mismo motivo")
    void cadaCuentaBloqueadaQuedaAuditada() {
        // Arrange
        Cuenta activa = cuenta("1000000001", EstadoCuenta.ACTIVA, BigDecimal.ZERO);
        prepararCliente(EstadoCliente.ACTIVO, activa);
        when(clienteEstado.inactivar(CLIENTE_ID, ADMIN_ID, MOTIVO))
                .thenReturn(new InactivacionRegistrada(ADMIN_ID, MOTIVO, FECHA));
        ArgumentCaptor<MovimientoAdministrativoCuenta> captor =
                ArgumentCaptor.forClass(MovimientoAdministrativoCuenta.class);

        // Act
        inactivacionService.inactivar(CLIENTE_ID, ADMIN_ID, request());

        // Assert
        verify(movimientoCuentaRepository).save(captor.capture());
        MovimientoAdministrativoCuenta movimiento = captor.getValue();
        assertThat(movimiento.getCuentaId()).isEqualTo(activa.getId());
        assertThat(movimiento.getTipoAccion()).isEqualTo(TipoAccionCuenta.BLOQUEO);
        assertThat(movimiento.getEstadoAnterior()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(movimiento.getEstadoNuevo()).isEqualTo(EstadoCuenta.BLOQUEADA);
        assertThat(movimiento.getUsuarioId()).isEqualTo(ADMIN_ID);
        assertThat(movimiento.getMotivo()).isEqualTo(MOTIVO);
    }

    @Test
    @DisplayName("Un cliente sin cuentas tambien se puede inactivar")
    void clienteSinCuentasSeInactiva() {
        // Arrange
        prepararCliente(EstadoCliente.ACTIVO);
        when(clienteEstado.inactivar(CLIENTE_ID, ADMIN_ID, MOTIVO))
                .thenReturn(new InactivacionRegistrada(ADMIN_ID, MOTIVO, FECHA));

        // Act
        InactivacionClienteResponse respuesta = inactivacionService.inactivar(CLIENTE_ID, ADMIN_ID, request());

        // Assert
        assertThat(respuesta.cuentasBloqueadas()).isEmpty();
        verify(clienteEstado).inactivar(CLIENTE_ID, ADMIN_ID, MOTIVO);
        verifyNoInteractions(movimientoCuentaRepository);
    }

    @Test
    @DisplayName("AC-2: si alguna cuenta tiene saldo responde 409 'El cliente tiene saldos pendientes' y no cambia nada")
    void clienteConSaldoRespondeConflict() {
        // Arrange
        Cuenta sinSaldo = cuenta("1000000001", EstadoCuenta.ACTIVA, BigDecimal.ZERO);
        Cuenta conSaldo = cuenta("1000000002", EstadoCuenta.BLOQUEADA, new BigDecimal("0.01"));
        prepararCliente(EstadoCliente.ACTIVO, sinSaldo, conSaldo);

        // Act & Assert
        assertThatThrownBy(() -> inactivacionService.inactivar(CLIENTE_ID, ADMIN_ID, request()))
                .isInstanceOfSatisfying(ClienteConSaldosPendientesException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("El cliente tiene saldos pendientes");
                    assertThat(ex.getErrorCode()).isEqualTo("CLIENTE_CON_SALDOS_PENDIENTES");
                });

        assertThat(sinSaldo.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        verify(cuentaRepository, never()).save(any());
        verifyNoInteractions(movimientoCuentaRepository);
        verify(clienteEstado, never()).inactivar(any(), any(), any());
    }

    @Test
    @DisplayName("AC-4: un cliente ya INACTIVO responde 409 CLIENTE_YA_INACTIVO sin tocar sus cuentas")
    void clienteYaInactivoRespondeConflict() {
        // Arrange
        when(clienteEstado.bloquearYObtenerEstado(CLIENTE_ID)).thenReturn(Optional.of(EstadoCliente.INACTIVO));

        // Act & Assert
        assertThatThrownBy(() -> inactivacionService.inactivar(CLIENTE_ID, ADMIN_ID, request()))
                .isInstanceOfSatisfying(ClienteYaInactivoException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getErrorCode()).isEqualTo("CLIENTE_YA_INACTIVO");
                });

        verifyNoInteractions(cuentaRepository, movimientoCuentaRepository);
        verify(clienteEstado, never()).inactivar(any(), any(), any());
    }

    @Test
    @DisplayName("Un cliente inexistente responde 404 CLIENTE_NO_ENCONTRADO")
    void clienteInexistenteRespondeNotFound() {
        // Arrange
        when(clienteEstado.bloquearYObtenerEstado(CLIENTE_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> inactivacionService.inactivar(CLIENTE_ID, ADMIN_ID, request()))
                .isInstanceOfSatisfying(ClienteNoEncontradoException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getErrorCode()).isEqualTo("CLIENTE_NO_ENCONTRADO");
                });

        verifyNoInteractions(cuentaRepository, movimientoCuentaRepository);
    }

    @Test
    @DisplayName("Se bloquea primero el cliente y despues sus cuentas, en orden de id (ADR-0007)")
    void seBloqueaElClienteYLuegoLasCuentasEnOrden() {
        // Arrange
        Cuenta primera = cuenta("1000000001", EstadoCuenta.ACTIVA, BigDecimal.ZERO);
        Cuenta segunda = cuenta("1000000002", EstadoCuenta.ACTIVA, BigDecimal.ZERO);
        List<Cuenta> ordenadas = new ArrayList<>(List.of(primera, segunda));
        ordenadas.sort((a, b) -> a.getId().compareTo(b.getId()));
        // El repositorio devuelve los ids al reves del orden esperado.
        prepararCliente(EstadoCliente.ACTIVO, ordenadas.get(1), ordenadas.get(0));
        when(clienteEstado.inactivar(CLIENTE_ID, ADMIN_ID, MOTIVO))
                .thenReturn(new InactivacionRegistrada(ADMIN_ID, MOTIVO, FECHA));

        // Act
        inactivacionService.inactivar(CLIENTE_ID, ADMIN_ID, request());

        // Assert
        InOrder orden = inOrder(clienteEstado, cuentaRepository);
        orden.verify(clienteEstado).bloquearYObtenerEstado(CLIENTE_ID);
        orden.verify(cuentaRepository).findByIdForUpdate(ordenadas.get(0).getId());
        orden.verify(cuentaRepository).findByIdForUpdate(ordenadas.get(1).getId());
        orden.verify(clienteEstado).inactivar(CLIENTE_ID, ADMIN_ID, MOTIVO);
    }

    private void prepararCliente(EstadoCliente estado, Cuenta... cuentas) {
        when(clienteEstado.bloquearYObtenerEstado(CLIENTE_ID)).thenReturn(Optional.of(estado));
        List<UUID> ids = new ArrayList<>();
        for (Cuenta cuenta : cuentas) {
            ids.add(cuenta.getId());
            when(cuentaRepository.findByIdForUpdate(cuenta.getId())).thenReturn(Optional.of(cuenta));
        }
        when(cuentaRepository.findIdsByClienteId(CLIENTE_ID)).thenReturn(ids);
    }

    private Cuenta cuenta(String numero, EstadoCuenta estado, BigDecimal saldo) {
        Cuenta cuenta = new Cuenta(CLIENTE_ID, numero, TipoCuenta.AHORROS, saldo);
        ReflectionTestUtils.setField(cuenta, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(cuenta, "estado", estado);
        return cuenta;
    }

    private InactivacionClienteRequest request() {
        return new InactivacionClienteRequest(MOTIVO);
    }
}
