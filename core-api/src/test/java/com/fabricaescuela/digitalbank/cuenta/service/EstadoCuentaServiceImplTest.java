package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cuenta.dto.CambioEstadoCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CambioEstadoCuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.MovimientoAdministrativoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoAccionCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoDisponibleException;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.IMovimientoAdministrativoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EstadoCuentaServiceImpl - bloqueo, desbloqueo y cierre de cuentas")
class EstadoCuentaServiceImplTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final String NUMERO_CUENTA = "1234567890";
    private static final String MOTIVO = "Actividad sospechosa detectada";
    private static final BigDecimal SALDO_CON_DINERO = new BigDecimal("100000.00");

    @Mock
    private ICuentaRepository cuentaRepository;

    @Mock
    private IMovimientoAdministrativoRepository movimientoRepository;

    @InjectMocks
    private EstadoCuentaServiceImpl estadoCuentaService;


    @Test
    @DisplayName("Bloquear una cuenta ACTIVA la deja BLOQUEADA y registra la auditoria")
    void bloquearCuentaActivaLaDejaBloqueada() {
        
        Cuenta cuenta = cuenta(EstadoCuenta.ACTIVA, SALDO_CON_DINERO);
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(movimientoRepository.save(any(MovimientoAdministrativoCuenta.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        
        CambioEstadoCuentaResponse respuesta =
                estadoCuentaService.bloquear(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO));

        
        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.BLOQUEADA);
        assertThat(respuesta.numeroCuenta()).isEqualTo(NUMERO_CUENTA);
        assertThat(respuesta.accion()).isEqualTo(TipoAccionCuenta.BLOQUEO);
        assertThat(respuesta.estadoAnterior()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(respuesta.estadoNuevo()).isEqualTo(EstadoCuenta.BLOQUEADA);
        assertThat(respuesta.motivo()).isEqualTo(MOTIVO);
        assertThat(respuesta.usuarioResponsableId()).isEqualTo(USUARIO_ID);
        assertThat(respuesta.fechaHora()).isNotNull();
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    @DisplayName("El bloqueo guarda la auditoria con el responsable, el motivo y los estados")
    void bloquearGuardaLaAuditoria() {
        
        Cuenta cuenta = cuenta(EstadoCuenta.ACTIVA, SALDO_CON_DINERO);
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(movimientoRepository.save(any(MovimientoAdministrativoCuenta.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        ArgumentCaptor<MovimientoAdministrativoCuenta> captor =
                ArgumentCaptor.forClass(MovimientoAdministrativoCuenta.class);

        
        estadoCuentaService.bloquear(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO));

        
        verify(movimientoRepository).save(captor.capture());
        MovimientoAdministrativoCuenta guardado = captor.getValue();
        assertThat(guardado.getTipoAccion()).isEqualTo(TipoAccionCuenta.BLOQUEO);
        assertThat(guardado.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(guardado.getMotivo()).isEqualTo(MOTIVO);
        assertThat(guardado.getEstadoAnterior()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(guardado.getEstadoNuevo()).isEqualTo(EstadoCuenta.BLOQUEADA);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCuenta.class, names = {"BLOQUEADA", "CERRADA"})
    @DisplayName("Bloquear una cuenta que no esta ACTIVA responde 409 y no guarda nada")
    void bloquearCuentaNoActivaResponde409(EstadoCuenta estado) {
        
        Cuenta cuenta = cuenta(estado, SALDO_CON_DINERO);
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        
        assertThatThrownBy(() ->
                estadoCuentaService.bloquear(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO)))
                .isInstanceOf(CuentaNoDisponibleException.class)
                .hasMessage("La cuenta no está activa")
                .extracting(ex -> ((CuentaNoDisponibleException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(cuenta.getEstado()).isEqualTo(estado);
        verify(cuentaRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Bloquear una cuenta inexistente responde 404")
    void bloquearCuentaInexistenteResponde404() {
        
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.empty());

        
        assertThatThrownBy(() ->
                estadoCuentaService.bloquear(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO)))
                .isInstanceOf(CuentaNoEncontradaException.class)
                .extracting(ex -> ((CuentaNoEncontradaException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(movimientoRepository, never()).save(any());
    }



    @Test
    @DisplayName("Desbloquear una cuenta BLOQUEADA la deja ACTIVA y registra la auditoria")
    void desbloquearCuentaBloqueadaLaDejaActiva() {
        
        Cuenta cuenta = cuenta(EstadoCuenta.BLOQUEADA, SALDO_CON_DINERO);
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(movimientoRepository.save(any(MovimientoAdministrativoCuenta.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        ArgumentCaptor<MovimientoAdministrativoCuenta> captor =
                ArgumentCaptor.forClass(MovimientoAdministrativoCuenta.class);

        
        CambioEstadoCuentaResponse respuesta =
                estadoCuentaService.desbloquear(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO));

        
        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(respuesta.accion()).isEqualTo(TipoAccionCuenta.DESBLOQUEO);
        assertThat(respuesta.estadoAnterior()).isEqualTo(EstadoCuenta.BLOQUEADA);
        assertThat(respuesta.estadoNuevo()).isEqualTo(EstadoCuenta.ACTIVA);
        verify(cuentaRepository).save(cuenta);
        verify(movimientoRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoAccion()).isEqualTo(TipoAccionCuenta.DESBLOQUEO);
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(USUARIO_ID);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCuenta.class, names = {"ACTIVA", "CERRADA"})
    @DisplayName("Desbloquear una cuenta que no esta BLOQUEADA responde 409 y no guarda nada")
    void desbloquearCuentaNoBloqueadaResponde409(EstadoCuenta estado) {
        
        Cuenta cuenta = cuenta(estado, SALDO_CON_DINERO);
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        
        assertThatThrownBy(() ->
                estadoCuentaService.desbloquear(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO)))
                .isInstanceOf(CuentaNoDisponibleException.class)
                .hasMessage("La cuenta no está bloqueada")
                .extracting(ex -> ((CuentaNoDisponibleException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(cuenta.getEstado()).isEqualTo(estado);
        verify(cuentaRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Desbloquear una cuenta inexistente responde 404")
    void desbloquearCuentaInexistenteResponde404() {
        
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.empty());

        
        assertThatThrownBy(() ->
                estadoCuentaService.desbloquear(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO)))
                .isInstanceOf(CuentaNoEncontradaException.class);

        verify(movimientoRepository, never()).save(any());
    }



    @Test
    @DisplayName("Cerrar una cuenta ACTIVA con saldo $0 la deja CERRADA y registra la auditoria")
    void cerrarCuentaActivaConSaldoCeroLaDejaCerrada() {
        
        Cuenta cuenta = cuenta(EstadoCuenta.ACTIVA, BigDecimal.ZERO);
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(movimientoRepository.save(any(MovimientoAdministrativoCuenta.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        ArgumentCaptor<MovimientoAdministrativoCuenta> captor =
                ArgumentCaptor.forClass(MovimientoAdministrativoCuenta.class);

        
        CambioEstadoCuentaResponse respuesta =
                estadoCuentaService.cerrar(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO));

        
        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.CERRADA);
        assertThat(respuesta.numeroCuenta()).isEqualTo(NUMERO_CUENTA);
        assertThat(respuesta.accion()).isEqualTo(TipoAccionCuenta.CIERRE);
        assertThat(respuesta.estadoAnterior()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(respuesta.estadoNuevo()).isEqualTo(EstadoCuenta.CERRADA);
        assertThat(respuesta.usuarioResponsableId()).isEqualTo(USUARIO_ID);
        verify(cuentaRepository).save(cuenta);
        verify(movimientoRepository).save(captor.capture());
        MovimientoAdministrativoCuenta guardado = captor.getValue();
        assertThat(guardado.getTipoAccion()).isEqualTo(TipoAccionCuenta.CIERRE);
        assertThat(guardado.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(guardado.getMotivo()).isEqualTo(MOTIVO);
        assertThat(guardado.getEstadoAnterior()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(guardado.getEstadoNuevo()).isEqualTo(EstadoCuenta.CERRADA);
    }

    @Test
    @DisplayName("Cerrar una cuenta BLOQUEADA con saldo $0 esta permitido")
    void cerrarCuentaBloqueadaConSaldoCeroEstaPermitido() {
        
        Cuenta cuenta = cuenta(EstadoCuenta.BLOQUEADA, BigDecimal.ZERO);
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(movimientoRepository.save(any(MovimientoAdministrativoCuenta.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        
        CambioEstadoCuentaResponse respuesta =
                estadoCuentaService.cerrar(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO));

        
        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.CERRADA);
        assertThat(respuesta.estadoAnterior()).isEqualTo(EstadoCuenta.BLOQUEADA);
        assertThat(respuesta.estadoNuevo()).isEqualTo(EstadoCuenta.CERRADA);
    }

    @Test
    @DisplayName("El saldo 0.00 cuenta como saldo $0")
    void elSaldoCeroConDecimalesCuentaComoCero() {
        
        Cuenta cuenta = cuenta(EstadoCuenta.ACTIVA, new BigDecimal("0.00"));
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(movimientoRepository.save(any(MovimientoAdministrativoCuenta.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        
        estadoCuentaService.cerrar(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO));

        
        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuenta.CERRADA);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCuenta.class, names = {"ACTIVA", "BLOQUEADA"})
    @DisplayName("Cerrar una cuenta con saldo distinto de $0 responde 409 y no guarda nada")
    void cerrarCuentaConSaldoResponde409(EstadoCuenta estado) {
        
        Cuenta cuenta = cuenta(estado, new BigDecimal("0.01"));
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        
        assertThatThrownBy(() ->
                estadoCuentaService.cerrar(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO)))
                .isInstanceOf(CuentaNoDisponibleException.class)
                .hasMessage("La cuenta debe tener saldo $0 para cerrarse")
                .extracting(ex -> ((CuentaNoDisponibleException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(cuenta.getEstado()).isEqualTo(estado);
        verify(cuentaRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cerrar una cuenta ya CERRADA responde 409 'La cuenta ya esta cerrada'")
    void cerrarCuentaYaCerradaResponde409() {
        
        Cuenta cuenta = cuenta(EstadoCuenta.CERRADA, BigDecimal.ZERO);
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        
        assertThatThrownBy(() ->
                estadoCuentaService.cerrar(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO)))
                .isInstanceOf(CuentaNoDisponibleException.class)
                .hasMessage("La cuenta ya está cerrada")
                .extracting(ex -> ((CuentaNoDisponibleException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(cuentaRepository, never()).save(any());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una cuenta ya CERRADA con saldo recibe 'ya esta cerrada', no el mensaje del saldo")
    void cuentaCerradaConSaldoRecibeMensajeDeYaCerrada() {
        
        Cuenta cuenta = cuenta(EstadoCuenta.CERRADA, SALDO_CON_DINERO);
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        
        assertThatThrownBy(() ->
                estadoCuentaService.cerrar(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO)))
                .isInstanceOf(CuentaNoDisponibleException.class)
                .hasMessage("La cuenta ya está cerrada");
    }

    @Test
    @DisplayName("Cerrar una cuenta inexistente responde 404")
    void cerrarCuentaInexistenteResponde404() {
       
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.empty());

        
        assertThatThrownBy(() ->
                estadoCuentaService.cerrar(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO)))
                .isInstanceOf(CuentaNoEncontradaException.class)
                .extracting(ex -> ((CuentaNoEncontradaException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(movimientoRepository, never()).save(any());
    }



    @Test
    @DisplayName("Bloquear usa el bloqueo de fila y nunca la lectura simple de la cuenta")
    void bloquearUsaElBloqueoDeFilaYNuncaFindById() {
        
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID))
                .thenReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA, SALDO_CON_DINERO)));
        when(movimientoRepository.save(any(MovimientoAdministrativoCuenta.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        
        estadoCuentaService.bloquear(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO));

        
        verify(cuentaRepository).findByIdForUpdate(CUENTA_ID);
        verify(cuentaRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Cerrar usa el bloqueo de fila y nunca la lectura simple de la cuenta")
    void cerrarUsaElBloqueoDeFilaYNuncaFindById() {
        
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID))
                .thenReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA, BigDecimal.ZERO)));
        when(movimientoRepository.save(any(MovimientoAdministrativoCuenta.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        
        estadoCuentaService.cerrar(CUENTA_ID, USUARIO_ID, new CambioEstadoCuentaRequest(MOTIVO));

        
        verify(cuentaRepository).findByIdForUpdate(CUENTA_ID);
        verify(cuentaRepository, never()).findById(any());
    }

    private Cuenta cuenta(EstadoCuenta estado, BigDecimal saldo) {
        Cuenta cuenta = new Cuenta(UUID.randomUUID(), NUMERO_CUENTA, TipoCuenta.AHORROS, saldo);
        ReflectionTestUtils.setField(cuenta, "estado", estado);
        return cuenta;
    }
}