package com.fabricaescuela.digitalbank.transaccion.service;

import com.fabricaescuela.digitalbank.cuenta.dto.MovimientoSaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.exception.SaldoReversoInsuficienteException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;
import com.fabricaescuela.digitalbank.transaccion.dto.ReversoRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.ReversoResponse;
import com.fabricaescuela.digitalbank.transaccion.entity.EstadoTransaccion;
import com.fabricaescuela.digitalbank.transaccion.entity.OrigenDeposito;
import com.fabricaescuela.digitalbank.transaccion.entity.SentidoTransferencia;
import com.fabricaescuela.digitalbank.transaccion.entity.TipoTransaccion;
import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;
import com.fabricaescuela.digitalbank.transaccion.exception.TransaccionNoEncontradaException;
import com.fabricaescuela.digitalbank.transaccion.exception.TransaccionNoReversableException;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("ReversoServiceImpl - reverso de transacciones (HU-12)")
class ReversoServiceImplTest {

    private static final UUID DEPOSITO_ID = UUID.randomUUID();
    private static final UUID RETIRO_ID = UUID.randomUUID();
    private static final UUID SALIDA_ID = UUID.randomUUID();
    private static final UUID ENTRADA_ID = UUID.randomUUID();
    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final UUID ORIGEN_ID = UUID.randomUUID();
    private static final UUID DESTINO_ID = UUID.randomUUID();
    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID TRANSFERENCIA_ID = UUID.randomUUID();
    private static final BigDecimal MONTO = new BigDecimal("30000.00");
    private static final String MOTIVO = "Movimiento duplicado por error del cajero";

    @Mock
    private ICuentaService cuentaService;

    @Mock
    private ITransaccionRepository transaccionRepository;

    @Captor
    private ArgumentCaptor<Transaccion> transaccionCaptor;

    @InjectMocks
    private ReversoServiceImpl reversoService;

    @Test
    @DisplayName("Reversar un depósito debita la cuenta y deja la original en REVERSADA")
    void reversarDepositoDebitaYMarcaReversada() {
        Transaccion deposito = deposito(DEPOSITO_ID);
        when(transaccionRepository.findById(DEPOSITO_ID)).thenReturn(Optional.of(deposito));
        when(transaccionRepository.findByIdForUpdate(DEPOSITO_ID)).thenReturn(Optional.of(deposito));
        when(cuentaService.aplicarReverso(CUENTA_ID, MONTO, false))
                .thenReturn(new MovimientoSaldoResponse(CUENTA_ID, new BigDecimal("100000.00"), new BigDecimal("70000.00")));
        guardarAsignandoId();

        ReversoResponse response = reversoService.reversar(DEPOSITO_ID, request(), USUARIO_ID);

        // El depósito se deshace debitando (credito=false)
        verify(cuentaService).aplicarReverso(CUENTA_ID, MONTO, false);

        verify(transaccionRepository, times(2)).save(transaccionCaptor.capture());
        Transaccion reverso = transaccionCaptor.getAllValues().get(0);
        assertThat(reverso.getTipo()).isEqualTo(TipoTransaccion.REVERSO);
        assertThat(reverso.getSentido()).isEqualTo(SentidoTransferencia.SALIENTE);
        assertThat(reverso.getCuentaId()).isEqualTo(CUENTA_ID);
        assertThat(reverso.getMonto()).isEqualByComparingTo(MONTO);
        assertThat(reverso.getTransaccionRelacionadaId()).isEqualTo(DEPOSITO_ID);
        assertThat(reverso.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(reverso.getMotivo()).isEqualTo(MOTIVO);
        assertThat(reverso.getTransferenciaId()).isNull();

        assertThat(deposito.getEstado()).isEqualTo(EstadoTransaccion.REVERSADA);
        assertThat(deposito.getTransaccionRelacionadaId()).isEqualTo(reverso.getId());

        assertThat(response.transaccionOriginalId()).isEqualTo(DEPOSITO_ID);
        assertThat(response.tipoOriginal()).isEqualTo("DEPOSITO");
        assertThat(response.estadoOriginal()).isEqualTo("REVERSADA");
        assertThat(response.reversos()).hasSize(1);
        assertThat(response.reversos().get(0).sentido()).isEqualTo("SALIENTE");
    }

    @Test
    @DisplayName("Reversar un retiro acredita la cuenta de vuelta")
    void reversarRetiroAcredita() {
        Transaccion retiro = retiro(RETIRO_ID);
        when(transaccionRepository.findById(RETIRO_ID)).thenReturn(Optional.of(retiro));
        when(transaccionRepository.findByIdForUpdate(RETIRO_ID)).thenReturn(Optional.of(retiro));
        when(cuentaService.aplicarReverso(CUENTA_ID, MONTO, true))
                .thenReturn(new MovimientoSaldoResponse(CUENTA_ID, new BigDecimal("10000.00"), new BigDecimal("40000.00")));
        guardarAsignandoId();

        ReversoResponse response = reversoService.reversar(RETIRO_ID, request(), USUARIO_ID);

        // El retiro se deshace acreditando (credito=true)
        verify(cuentaService).aplicarReverso(CUENTA_ID, MONTO, true);

        verify(transaccionRepository, times(2)).save(transaccionCaptor.capture());
        Transaccion reverso = transaccionCaptor.getAllValues().get(0);
        assertThat(reverso.getSentido()).isEqualTo(SentidoTransferencia.ENTRANTE);
        assertThat(retiro.getEstado()).isEqualTo(EstadoTransaccion.REVERSADA);
        assertThat(response.tipoOriginal()).isEqualTo("RETIRO");
        assertThat(response.reversos()).hasSize(1);
    }

    @Test
    @DisplayName("Reversar una transferencia acredita el origen, debita el destino y crea dos filas REVERSO")
    void reversarTransferenciaAjustaAmbasCuentas() {
        Transaccion salida = transferenciaLeg(SALIDA_ID, ORIGEN_ID, SentidoTransferencia.SALIENTE);
        Transaccion entrada = transferenciaLeg(ENTRADA_ID, DESTINO_ID, SentidoTransferencia.ENTRANTE);
        when(transaccionRepository.findById(SALIDA_ID)).thenReturn(Optional.of(salida));
        when(transaccionRepository.findByTransferenciaIdForUpdate(TRANSFERENCIA_ID))
                .thenReturn(List.of(salida, entrada));
        when(cuentaService.aplicarReverso(ORIGEN_ID, MONTO, true))
                .thenReturn(new MovimientoSaldoResponse(ORIGEN_ID, new BigDecimal("70000.00"), new BigDecimal("100000.00")));
        when(cuentaService.aplicarReverso(DESTINO_ID, MONTO, false))
                .thenReturn(new MovimientoSaldoResponse(DESTINO_ID, new BigDecimal("35000.00"), new BigDecimal("5000.00")));
        guardarAsignandoId();

        ReversoResponse response = reversoService.reversar(SALIDA_ID, request(), USUARIO_ID);

        verify(cuentaService).bloquearEnOrden(ORIGEN_ID, DESTINO_ID);
        verify(cuentaService).aplicarReverso(ORIGEN_ID, MONTO, true);
        verify(cuentaService).aplicarReverso(DESTINO_ID, MONTO, false);

        verify(transaccionRepository, times(4)).save(transaccionCaptor.capture());
        Transaccion reversoOrigen = transaccionCaptor.getAllValues().get(0);
        Transaccion reversoDestino = transaccionCaptor.getAllValues().get(1);

        assertThat(reversoOrigen.getTipo()).isEqualTo(TipoTransaccion.REVERSO);
        assertThat(reversoOrigen.getCuentaId()).isEqualTo(ORIGEN_ID);
        assertThat(reversoOrigen.getSentido()).isEqualTo(SentidoTransferencia.ENTRANTE);
        assertThat(reversoOrigen.getTransferenciaId()).isEqualTo(TRANSFERENCIA_ID);

        assertThat(reversoDestino.getCuentaId()).isEqualTo(DESTINO_ID);
        assertThat(reversoDestino.getSentido()).isEqualTo(SentidoTransferencia.SALIENTE);
        assertThat(reversoDestino.getTransferenciaId()).isEqualTo(TRANSFERENCIA_ID);

        assertThat(salida.getEstado()).isEqualTo(EstadoTransaccion.REVERSADA);
        assertThat(entrada.getEstado()).isEqualTo(EstadoTransaccion.REVERSADA);

        assertThat(response.tipoOriginal()).isEqualTo("TRANSFERENCIA");
        assertThat(response.reversos()).hasSize(2);
    }

    @Test
    @DisplayName("Transacción inexistente responde 404 TRANSACCION_NO_ENCONTRADA")
    void transaccionInexistenteResponde404() {
        when(transaccionRepository.findById(DEPOSITO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reversoService.reversar(DEPOSITO_ID, request(), USUARIO_ID))
                .isInstanceOfSatisfying(TransaccionNoEncontradaException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getErrorCode()).isEqualTo("TRANSACCION_NO_ENCONTRADA");
                });

        verifyNoInteractions(cuentaService);
    }

    @Test
    @DisplayName("Un reverso no se puede reversar: responde 409 TRANSACCION_NO_REVERSABLE")
    void reversarUnReversoResponde409() {
        Transaccion reverso = Transaccion.reverso(CUENTA_ID, MONTO, BigDecimal.ZERO, BigDecimal.ZERO,
                SentidoTransferencia.SALIENTE, null, DEPOSITO_ID, USUARIO_ID, MOTIVO);
        ReflectionTestUtils.setField(reverso, "id", UUID.randomUUID());
        when(transaccionRepository.findById(reverso.getId())).thenReturn(Optional.of(reverso));

        assertThatThrownBy(() -> reversoService.reversar(reverso.getId(), request(), USUARIO_ID))
                .isInstanceOfSatisfying(TransaccionNoReversableException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(cuentaService);
    }

    @Test
    @DisplayName("Reversar un depósito ya reversado responde 409 TRANSACCION_NO_REVERSABLE")
    void depositoYaReversadoResponde409() {
        Transaccion deposito = deposito(DEPOSITO_ID);
        deposito.marcarReversada(UUID.randomUUID());
        when(transaccionRepository.findById(DEPOSITO_ID)).thenReturn(Optional.of(deposito));
        when(transaccionRepository.findByIdForUpdate(DEPOSITO_ID)).thenReturn(Optional.of(deposito));

        assertThatThrownBy(() -> reversoService.reversar(DEPOSITO_ID, request(), USUARIO_ID))
                .isInstanceOfSatisfying(TransaccionNoReversableException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo("TRANSACCION_NO_REVERSABLE"));

        verifyNoInteractions(cuentaService);
        verify(transaccionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reversar una transferencia ya reversada responde 409 TRANSACCION_NO_REVERSABLE")
    void transferenciaYaReversadaResponde409() {
        Transaccion salida = transferenciaLeg(SALIDA_ID, ORIGEN_ID, SentidoTransferencia.SALIENTE);
        Transaccion entrada = transferenciaLeg(ENTRADA_ID, DESTINO_ID, SentidoTransferencia.ENTRANTE);
        salida.marcarReversada(UUID.randomUUID());
        entrada.marcarReversada(UUID.randomUUID());
        when(transaccionRepository.findById(SALIDA_ID)).thenReturn(Optional.of(salida));
        when(transaccionRepository.findByTransferenciaIdForUpdate(TRANSFERENCIA_ID))
                .thenReturn(List.of(salida, entrada));

        assertThatThrownBy(() -> reversoService.reversar(SALIDA_ID, request(), USUARIO_ID))
                .isInstanceOf(TransaccionNoReversableException.class);

        verifyNoInteractions(cuentaService);
        verify(transaccionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Si no hay saldo para reversar el depósito, la excepción se propaga y no se registra nada")
    void saldoInsuficienteSePropaga() {
        Transaccion deposito = deposito(DEPOSITO_ID);
        when(transaccionRepository.findById(DEPOSITO_ID)).thenReturn(Optional.of(deposito));
        when(transaccionRepository.findByIdForUpdate(DEPOSITO_ID)).thenReturn(Optional.of(deposito));
        when(cuentaService.aplicarReverso(CUENTA_ID, MONTO, false))
                .thenThrow(new SaldoReversoInsuficienteException());

        assertThatThrownBy(() -> reversoService.reversar(DEPOSITO_ID, request(), USUARIO_ID))
                .isInstanceOfSatisfying(SaldoReversoInsuficienteException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo("Saldo insuficiente para reversar");
                });

        verify(transaccionRepository, never()).save(any());
    }

    // ---- helpers ----

    private void guardarAsignandoId() {
        when(transaccionRepository.save(any(Transaccion.class))).thenAnswer(invocacion -> {
            Transaccion t = invocacion.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
            }
            return t;
        });
    }

    private Transaccion deposito(UUID id) {
        Transaccion t = Transaccion.deposito(CUENTA_ID, MONTO, new BigDecimal("70000.00"),
                new BigDecimal("100000.00"), OrigenDeposito.EFECTIVO);
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private Transaccion retiro(UUID id) {
        Transaccion t = Transaccion.retiro(CUENTA_ID, MONTO, new BigDecimal("40000.00"),
                new BigDecimal("10000.00"));
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private Transaccion transferenciaLeg(UUID id, UUID cuentaId, SentidoTransferencia sentido) {
        Transaccion t = Transaccion.transferencia(cuentaId, MONTO, new BigDecimal("100000.00"),
                new BigDecimal("70000.00"), TRANSFERENCIA_ID, sentido, "Pago arriendo");
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private ReversoRequest request() {
        return new ReversoRequest(MOTIVO);
    }
}
