package com.fabricaescuela.digitalbank.transaccion.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Transaccion - creacion de depositos y retiros")
class TransaccionTest {

    @Test
    @DisplayName("Un deposito queda COMPLETADO, tipado como DEPOSITO y con el rastro de saldos")
    void depositoQuedaCompletadoConElRastroDeSaldos() {
        // Arrange
        UUID cuentaId = UUID.randomUUID();
        BigDecimal monto = new BigDecimal("50000.00");
        BigDecimal saldoAnterior = new BigDecimal("100000.00");
        BigDecimal saldoNuevo = new BigDecimal("150000.00");
        LocalDateTime antesDeCrear = LocalDateTime.now();

        // Act
        Transaccion transaccion = Transaccion.deposito(
                cuentaId, monto, saldoAnterior, saldoNuevo, OrigenDeposito.EFECTIVO);

        // Assert
        assertThat(transaccion.getCuentaId()).isEqualTo(cuentaId);
        assertThat(transaccion.getTipo()).isEqualTo(TipoTransaccion.DEPOSITO);
        assertThat(transaccion.getMonto()).isEqualByComparingTo(monto);
        assertThat(transaccion.getSaldoAnterior()).isEqualByComparingTo(saldoAnterior);
        assertThat(transaccion.getSaldoNuevo()).isEqualByComparingTo(saldoNuevo);
        assertThat(transaccion.getOrigen()).isEqualTo(OrigenDeposito.EFECTIVO);
        assertThat(transaccion.getEstado()).isEqualTo(EstadoTransaccion.COMPLETADA);
        assertThat(transaccion.getFechaHora()).isAfterOrEqualTo(antesDeCrear);
    }

    @Test
    @DisplayName("El origen recibido se conserva sin sustituirse por un valor por defecto")
    void elOrigenRecibidoSeConserva() {
        // Arrange
        UUID cuentaId = UUID.randomUUID();

        // Act
        Transaccion transaccion = Transaccion.deposito(
                cuentaId, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, OrigenDeposito.CHEQUE);

        // Assert
        assertThat(transaccion.getOrigen()).isEqualTo(OrigenDeposito.CHEQUE);
    }

    @Test
    @DisplayName("Un deposito no pertenece a ninguna transferencia")
    void depositoNoTieneTransferenciaId() {
        // Arrange
        UUID cuentaId = UUID.randomUUID();

        // Act
        Transaccion transaccion = Transaccion.deposito(
                cuentaId, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, OrigenDeposito.EFECTIVO);

        // Assert
        assertThat(transaccion.getTransferenciaId()).isNull();
        assertThat(transaccion.getSentido()).isNull();
        assertThat(transaccion.getDescripcion()).isNull();
    }

    @Test
    @DisplayName("Un retiro queda COMPLETADO, tipado como RETIRO y con el rastro de saldos")
    void retiroQuedaCompletadoConElRastroDeSaldos() {
        // Arrange
        UUID cuentaId = UUID.randomUUID();
        BigDecimal monto = new BigDecimal("30000.00");
        BigDecimal saldoAnterior = new BigDecimal("100000.00");
        BigDecimal saldoNuevo = new BigDecimal("70000.00");
        LocalDateTime antesDeCrear = LocalDateTime.now();

        // Act
        Transaccion transaccion = Transaccion.retiro(cuentaId, monto, saldoAnterior, saldoNuevo);

        // Assert
        assertThat(transaccion.getCuentaId()).isEqualTo(cuentaId);
        assertThat(transaccion.getTipo()).isEqualTo(TipoTransaccion.RETIRO);
        assertThat(transaccion.getMonto()).isEqualByComparingTo(monto);
        assertThat(transaccion.getSaldoAnterior()).isEqualByComparingTo(saldoAnterior);
        assertThat(transaccion.getSaldoNuevo()).isEqualByComparingTo(saldoNuevo);
        assertThat(transaccion.getEstado()).isEqualTo(EstadoTransaccion.COMPLETADA);
        assertThat(transaccion.getFechaHora()).isAfterOrEqualTo(antesDeCrear);
    }

    @Test
    @DisplayName("Un retiro no tiene origen de deposito ni pertenece a ninguna transferencia")
    void retiroNoTieneOrigenNiTransferencia() {
        // Arrange
        UUID cuentaId = UUID.randomUUID();

        // Act
        Transaccion transaccion = Transaccion.retiro(cuentaId, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO);

        // Assert
        assertThat(transaccion.getOrigen()).isNull();
        assertThat(transaccion.getTransferenciaId()).isNull();
        assertThat(transaccion.getSentido()).isNull();
        assertThat(transaccion.getDescripcion()).isNull();
    }
}
