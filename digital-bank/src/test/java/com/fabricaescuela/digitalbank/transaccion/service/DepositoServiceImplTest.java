package com.fabricaescuela.digitalbank.transaccion.service;

import com.fabricaescuela.digitalbank.cuenta.dto.MovimientoSaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoDisponibleException;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;
import com.fabricaescuela.digitalbank.transaccion.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.entity.OrigenDeposito;
import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepositoServiceImpl - registro de depositos")
class DepositoServiceImplTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();

    @Mock
    private ICuentaService cuentaService;

    @Mock
    private ITransaccionRepository transaccionRepository;

    @Captor
    private ArgumentCaptor<Transaccion> transaccionCaptor;

    @InjectMocks
    private DepositoServiceImpl depositoService;

    @Test
    @DisplayName("El deposito acredita el monto en la cuenta a traves del servicio de cuentas")
    void elDepositoAcreditaLaCuenta() {
        // Arrange
        prepararAcreditacion(new BigDecimal("50000.00"), new BigDecimal("100000.00"), new BigDecimal("150000.00"));

        // Act
        depositoService.registrarDeposito(CUENTA_ID, new DepositoRequest(new BigDecimal("50000.00"), OrigenDeposito.EFECTIVO));

        // Assert
        verify(cuentaService).acreditar(CUENTA_ID, new BigDecimal("50000.00"));
    }

    @Test
    @DisplayName("La transaccion registra el saldo antes y despues del deposito")
    void laTransaccionRegistraElSaldoAnteriorYElNuevo() {
        // Arrange
        prepararAcreditacion(new BigDecimal("50000.00"), new BigDecimal("100000.00"), new BigDecimal("150000.00"));

        // Act
        depositoService.registrarDeposito(CUENTA_ID, new DepositoRequest(new BigDecimal("50000.00"), OrigenDeposito.CHEQUE));

        // Assert
        verify(transaccionRepository).save(transaccionCaptor.capture());
        Transaccion transaccion = transaccionCaptor.getValue();
        assertThat(transaccion.getCuentaId()).isEqualTo(CUENTA_ID);
        assertThat(transaccion.getMonto()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(transaccion.getSaldoAnterior()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(transaccion.getSaldoNuevo()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(transaccion.getOrigen()).isEqualTo(OrigenDeposito.CHEQUE);
    }

    @Test
    @DisplayName("La respuesta expone la transaccion COMPLETADA de tipo DEPOSITO")
    void laRespuestaExponeLaTransaccionCompletada() {
        // Arrange
        prepararAcreditacion(new BigDecimal("80000.00"), BigDecimal.ZERO, new BigDecimal("80000.00"));

        // Act
        TransaccionResponse response = depositoService.registrarDeposito(
                CUENTA_ID, new DepositoRequest(new BigDecimal("80000.00"), OrigenDeposito.OTRO));

        // Assert
        assertThat(response.cuentaId()).isEqualTo(CUENTA_ID);
        assertThat(response.tipo()).isEqualTo("DEPOSITO");
        assertThat(response.estado()).isEqualTo("COMPLETADA");
        assertThat(response.origen()).isEqualTo("OTRO");
        assertThat(response.monto()).isEqualByComparingTo(new BigDecimal("80000.00"));
        assertThat(response.saldoAnterior()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.saldoNuevo()).isEqualByComparingTo(new BigDecimal("80000.00"));
        assertThat(response.fechaHora()).isNotNull();
    }

    @Test
    @DisplayName("Si la cuenta no existe la excepcion se propaga y no se registra transaccion")
    void cuentaInexistentePropagaLaExcepcion() {
        // Arrange
        CuentaNoEncontradaException excepcion = new CuentaNoEncontradaException();
        when(cuentaService.acreditar(CUENTA_ID, new BigDecimal("50000.00"))).thenThrow(excepcion);

        // Act & Assert
        assertThatThrownBy(() -> depositoService.registrarDeposito(
                CUENTA_ID, new DepositoRequest(new BigDecimal("50000.00"), OrigenDeposito.EFECTIVO)))
                .isSameAs(excepcion);

        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("Si la cuenta esta CERRADA la excepcion se propaga y no se registra transaccion")
    void cuentaCerradaPropagaLaExcepcion() {
        // Arrange
        CuentaNoDisponibleException excepcion = new CuentaNoDisponibleException("Cuenta no disponible");
        when(cuentaService.acreditar(CUENTA_ID, new BigDecimal("50000.00"))).thenThrow(excepcion);

        // Act & Assert
        assertThatThrownBy(() -> depositoService.registrarDeposito(
                CUENTA_ID, new DepositoRequest(new BigDecimal("50000.00"), OrigenDeposito.EFECTIVO)))
                .isSameAs(excepcion);

        verifyNoInteractions(transaccionRepository);
    }

    private void prepararAcreditacion(BigDecimal monto, BigDecimal saldoAnterior, BigDecimal saldoNuevo) {
        when(cuentaService.acreditar(CUENTA_ID, monto))
                .thenReturn(new MovimientoSaldoResponse(CUENTA_ID, saldoAnterior, saldoNuevo));
        when(transaccionRepository.save(any(Transaccion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }
}
