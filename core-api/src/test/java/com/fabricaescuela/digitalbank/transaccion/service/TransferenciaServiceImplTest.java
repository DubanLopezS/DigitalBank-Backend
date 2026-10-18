package com.fabricaescuela.digitalbank.transaccion.service;

import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.dto.MovimientoSaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoDisponibleException;
import com.fabricaescuela.digitalbank.cuenta.exception.SaldoInsuficienteException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaResponse;
import com.fabricaescuela.digitalbank.transaccion.entity.SentidoTransferencia;
import com.fabricaescuela.digitalbank.transaccion.entity.TipoTransaccion;
import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;
import com.fabricaescuela.digitalbank.transaccion.exception.CuentaDestinoNoDisponibleException;
import com.fabricaescuela.digitalbank.transaccion.exception.CuentaDestinoNoEncontradaException;
import com.fabricaescuela.digitalbank.transaccion.exception.CuentaOrigenBloqueadaException;
import com.fabricaescuela.digitalbank.transaccion.exception.CuentaOrigenDestinoIgualesException;
import com.fabricaescuela.digitalbank.transaccion.exception.CuentaOrigenNoDisponibleException;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferenciaServiceImpl - registro de transferencias")
class TransferenciaServiceImplTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final UUID DESTINO_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ID = UUID.randomUUID();
    private static final String CUENTA_DESTINO = "1234567890";
    private static final BigDecimal MONTO = new BigDecimal("30000.00");

    @Mock
    private ICuentaService cuentaService;

    @Mock
    private ITransaccionRepository transaccionRepository;

    @Captor
    private ArgumentCaptor<Transaccion> transaccionCaptor;

    @InjectMocks
    private TransferenciaServiceImpl transferenciaService;

    @Test
    @DisplayName("La transferencia valida, busca el destino, bloquea, valida el destino, debita y acredita en ese orden")
    void laTransferenciaSigueElOrdenDePasos() {
        // Arrange
        prepararTransferencia();

        // Act
        transferenciaService.registrarTransferencia(CUENTA_ID, request(), CLIENTE_ID);

        // Assert
        InOrder orden = inOrder(cuentaService);
        orden.verify(cuentaService).validarTitularidad(CUENTA_ID, CLIENTE_ID);
        orden.verify(cuentaService).buscarIdPorNumero(CUENTA_DESTINO);
        orden.verify(cuentaService).bloquearEnOrden(CUENTA_ID, DESTINO_ID);
        orden.verify(cuentaService).estaCerrada(DESTINO_ID);
        orden.verify(cuentaService).debitar(CUENTA_ID, MONTO);
        orden.verify(cuentaService).acreditar(DESTINO_ID, MONTO);
    }

    @Test
    @DisplayName("Se guardan dos filas con el mismo transferenciaId, SALIENTE en la origen y ENTRANTE en la destino")
    void seGuardanDosFilasLigadasPorElMismoTransferenciaId() {
        // Arrange
        prepararTransferencia();

        // Act
        transferenciaService.registrarTransferencia(CUENTA_ID, request(), CLIENTE_ID);

        // Assert
        verify(transaccionRepository, times(2)).save(transaccionCaptor.capture());
        Transaccion salida = transaccionCaptor.getAllValues().get(0);
        Transaccion entrada = transaccionCaptor.getAllValues().get(1);

        assertThat(salida.getTransferenciaId()).isNotNull().isEqualTo(entrada.getTransferenciaId());

        assertThat(salida.getCuentaId()).isEqualTo(CUENTA_ID);
        assertThat(salida.getTipo()).isEqualTo(TipoTransaccion.TRANSFERENCIA);
        assertThat(salida.getSentido()).isEqualTo(SentidoTransferencia.SALIENTE);
        assertThat(salida.getMonto()).isEqualByComparingTo(MONTO);
        assertThat(salida.getSaldoAnterior()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(salida.getSaldoNuevo()).isEqualByComparingTo(new BigDecimal("70000.00"));
        assertThat(salida.getDescripcion()).isEqualTo("Pago arriendo");

        assertThat(entrada.getCuentaId()).isEqualTo(DESTINO_ID);
        assertThat(entrada.getTipo()).isEqualTo(TipoTransaccion.TRANSFERENCIA);
        assertThat(entrada.getSentido()).isEqualTo(SentidoTransferencia.ENTRANTE);
        assertThat(entrada.getMonto()).isEqualByComparingTo(MONTO);
        assertThat(entrada.getSaldoAnterior()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(entrada.getSaldoNuevo()).isEqualByComparingTo(new BigDecimal("35000.00"));
        assertThat(entrada.getDescripcion()).isEqualTo("Pago arriendo");
    }

    @Test
    @DisplayName("La respuesta es el comprobante de la transaccion de salida")
    void laRespuestaEsElComprobanteDeLaSalida() {
        // Arrange
        prepararTransferencia();

        // Act
        TransferenciaResponse response = transferenciaService.registrarTransferencia(CUENTA_ID, request(), CLIENTE_ID);

        // Assert
        verify(transaccionRepository, times(2)).save(transaccionCaptor.capture());
        Transaccion salida = transaccionCaptor.getAllValues().get(0);
        assertThat(response.transferenciaId()).isEqualTo(salida.getTransferenciaId());
        assertThat(response.cuentaOrigenId()).isEqualTo(CUENTA_ID);
        assertThat(response.cuentaDestino()).isEqualTo(CUENTA_DESTINO);
        assertThat(response.monto()).isEqualByComparingTo(MONTO);
        assertThat(response.descripcion()).isEqualTo("Pago arriendo");
        assertThat(response.estado()).isEqualTo("COMPLETADA");
        assertThat(response.fechaHora()).isEqualTo(salida.getFechaHora());
    }

    @Test
    @DisplayName("Si la cuenta no es del cliente la excepcion se propaga sin buscar el destino ni debitar")
    void cuentaAjenaPropagaLaExcepcion() {
        // Arrange
        AccesoNoAutorizadoException excepcion = new AccesoNoAutorizadoException();
        doThrow(excepcion).when(cuentaService).validarTitularidad(CUENTA_ID, CLIENTE_ID);

        // Act & Assert
        assertThatThrownBy(() -> transferenciaService.registrarTransferencia(CUENTA_ID, request(), CLIENTE_ID))
                .isSameAs(excepcion);

        verify(cuentaService, never()).buscarIdPorNumero(any());
        verify(cuentaService, never()).debitar(any(UUID.class), any(BigDecimal.class));
        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("HU-09c AC-1: cuenta destino inexistente responde 404 'Cuenta destino no encontrada' sin bloquear ni debitar")
    void destinoInexistenteRespondeNotFound() {
        // Arrange
        when(cuentaService.buscarIdPorNumero(CUENTA_DESTINO)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> transferenciaService.registrarTransferencia(CUENTA_ID, request(), CLIENTE_ID))
                .isInstanceOfSatisfying(CuentaDestinoNoEncontradaException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Cuenta destino no encontrada");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        verify(cuentaService, never()).bloquearEnOrden(any(), any());
        verify(cuentaService, never()).debitar(any(UUID.class), any(BigDecimal.class));
        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("HU-09c AC-2: cuenta destino cerrada responde 409 'Cuenta destino no disponible' sin debitar ni acreditar")
    void destinoCerradoRespondeConflict() {
        // Arrange
        when(cuentaService.buscarIdPorNumero(CUENTA_DESTINO)).thenReturn(Optional.of(DESTINO_ID));
        when(cuentaService.estaCerrada(CUENTA_ID)).thenReturn(false);
        when(cuentaService.estaCerrada(DESTINO_ID)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> transferenciaService.registrarTransferencia(CUENTA_ID, request(), CLIENTE_ID))
                .isInstanceOfSatisfying(CuentaDestinoNoDisponibleException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Cuenta destino no disponible");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(cuentaService).bloquearEnOrden(CUENTA_ID, DESTINO_ID);
        verify(cuentaService, never()).debitar(any(UUID.class), any(BigDecimal.class));
        verify(cuentaService, never()).acreditar(any(UUID.class), any(BigDecimal.class));
        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("HU-09b: cuenta origen bloqueada responde 409 'Cuenta origen bloqueada' sin debitar ni acreditar")
    void origenBloqueadoRespondeConflict() {
        // Arrange
        when(cuentaService.buscarIdPorNumero(CUENTA_DESTINO)).thenReturn(Optional.of(DESTINO_ID));
        when(cuentaService.estaBloqueada(CUENTA_ID)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> transferenciaService.registrarTransferencia(CUENTA_ID, request(), CLIENTE_ID))
                .isInstanceOfSatisfying(CuentaOrigenBloqueadaException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Cuenta origen bloqueada");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        InOrder orden = inOrder(cuentaService);
        orden.verify(cuentaService).validarTitularidad(CUENTA_ID, CLIENTE_ID);
        orden.verify(cuentaService).estaBloqueada(CUENTA_ID);
        verify(cuentaService, never()).debitar(any(UUID.class), any(BigDecimal.class));
        verify(cuentaService, never()).acreditar(any(UUID.class), any(BigDecimal.class));
        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("HU-09b: cuenta origen cerrada responde 409 'Cuenta origen no disponible' sin debitar ni acreditar")
    void origenCerradoRespondeConflict() {
        // Arrange
        when(cuentaService.buscarIdPorNumero(CUENTA_DESTINO)).thenReturn(Optional.of(DESTINO_ID));
        when(cuentaService.estaCerrada(CUENTA_ID)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> transferenciaService.registrarTransferencia(CUENTA_ID, request(), CLIENTE_ID))
                .isInstanceOfSatisfying(CuentaOrigenNoDisponibleException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Cuenta origen no disponible");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        InOrder orden = inOrder(cuentaService);
        orden.verify(cuentaService).validarTitularidad(CUENTA_ID, CLIENTE_ID);
        orden.verify(cuentaService).estaCerrada(CUENTA_ID);
        verify(cuentaService, never()).debitar(any(UUID.class), any(BigDecimal.class));
        verify(cuentaService, never()).acreditar(any(UUID.class), any(BigDecimal.class));
        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("HU-09b: cuenta origen igual a la destino responde 400 'Cuenta origen y destino no pueden ser iguales' sin debitar ni acreditar")
    void origenIgualADestinoRespondeBadRequest() {
        // Arrange
        when(cuentaService.buscarIdPorNumero(CUENTA_DESTINO)).thenReturn(Optional.of(CUENTA_ID));

        // Act & Assert
        assertThatThrownBy(() -> transferenciaService.registrarTransferencia(CUENTA_ID, request(), CLIENTE_ID))
                .isInstanceOfSatisfying(CuentaOrigenDestinoIgualesException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Cuenta origen y destino no pueden ser iguales");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        InOrder orden = inOrder(cuentaService);
        orden.verify(cuentaService).validarTitularidad(CUENTA_ID, CLIENTE_ID);
        orden.verify(cuentaService).buscarIdPorNumero(CUENTA_DESTINO);
        verify(cuentaService, never()).debitar(any(UUID.class), any(BigDecimal.class));
        verify(cuentaService, never()).acreditar(any(UUID.class), any(BigDecimal.class));
        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("Si el debito falla no se acredita el destino ni se registra transaccion")
    void siElDebitoFallaNoSeAcreditaNiSeRegistra() {
        // Arrange
        SaldoInsuficienteException excepcion = new SaldoInsuficienteException();
        when(cuentaService.buscarIdPorNumero(CUENTA_DESTINO)).thenReturn(Optional.of(DESTINO_ID));
        when(cuentaService.debitar(CUENTA_ID, MONTO)).thenThrow(excepcion);

        // Act & Assert
        assertThatThrownBy(() -> transferenciaService.registrarTransferencia(CUENTA_ID, request(), CLIENTE_ID))
                .isSameAs(excepcion);

        verify(cuentaService, never()).acreditar(any(UUID.class), any(BigDecimal.class));
        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("Si el credito falla no se registra transaccion")
    void siElCreditoFallaNoSeRegistra() {
        // Arrange
        CuentaNoDisponibleException excepcion = new CuentaNoDisponibleException("Cuenta no disponible");
        when(cuentaService.buscarIdPorNumero(CUENTA_DESTINO)).thenReturn(Optional.of(DESTINO_ID));
        when(cuentaService.debitar(CUENTA_ID, MONTO))
                .thenReturn(new MovimientoSaldoResponse(CUENTA_ID, new BigDecimal("100000.00"), new BigDecimal("70000.00")));
        when(cuentaService.acreditar(DESTINO_ID, MONTO)).thenThrow(excepcion);

        // Act & Assert
        assertThatThrownBy(() -> transferenciaService.registrarTransferencia(CUENTA_ID, request(), CLIENTE_ID))
                .isSameAs(excepcion);

        verifyNoInteractions(transaccionRepository);
    }

    private void prepararTransferencia() {
        when(cuentaService.buscarIdPorNumero(CUENTA_DESTINO)).thenReturn(Optional.of(DESTINO_ID));
        when(cuentaService.debitar(CUENTA_ID, MONTO))
                .thenReturn(new MovimientoSaldoResponse(CUENTA_ID, new BigDecimal("100000.00"), new BigDecimal("70000.00")));
        when(cuentaService.acreditar(DESTINO_ID, MONTO))
                .thenReturn(new MovimientoSaldoResponse(DESTINO_ID, new BigDecimal("5000.00"), new BigDecimal("35000.00")));
        when(transaccionRepository.save(any(Transaccion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private TransferenciaRequest request() {
        return new TransferenciaRequest(CUENTA_DESTINO, MONTO, "Pago arriendo");
    }
}
