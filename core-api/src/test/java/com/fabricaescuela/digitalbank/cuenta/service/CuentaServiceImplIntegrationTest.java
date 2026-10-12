package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaResponse;
import com.fabricaescuela.digitalbank.transaccion.entity.SentidoTransferencia;
import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.ITransferenciaService;
import com.fabricaescuela.digitalbank.transaccion.repository.TransaccionJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
class CuentaServiceImplIntegrationTest {

    @Autowired
    private ICuentaService cuentaService;

    @Autowired
    private ICuentaRepository cuentaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ITransferenciaService transferenciaService;

    @Autowired
    private TransaccionJpaRepository transaccionJpaRepository;

    @Test
    @DisplayName("Despues de validar la titularidad el bloqueo lee el saldo actual de la base de datos")
    void despuesDeValidarTitularidadElBloqueoLeeElSaldoActual() {
        // Arrange
        UUID clienteId = UUID.randomUUID();
        UUID cuentaId = cuentaRepository
                .save(new Cuenta(clienteId, "1234567890", TipoCuenta.AHORROS, new BigDecimal("100.00")))
                .getId();

        TransactionTemplate transaccionA = new TransactionTemplate(transactionManager);
        TransactionTemplate transaccionB = new TransactionTemplate(transactionManager);
        transaccionB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Act
        BigDecimal saldoLeido = transaccionA.execute(estadoA -> {
            cuentaService.validarTitularidad(cuentaId, clienteId);

            transaccionB.executeWithoutResult(estadoB -> {
                Cuenta cuenta = cuentaRepository.findById(cuentaId).orElseThrow();
                cuenta.debitar(new BigDecimal("30.00"));
                cuentaRepository.save(cuenta);
            });

            return cuentaRepository.findByIdForUpdate(cuentaId).orElseThrow().getSaldoContable();
        });

        // Assert
        assertThat(saldoLeido).isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("validarTitularidad responde 403 si el clienteId es null")
    void validarTitularidadConClienteNullRespondeForbidden() {
        // Arrange
        UUID cuentaId = cuentaRepository
                .save(new Cuenta(UUID.randomUUID(), "1234567891", TipoCuenta.AHORROS, new BigDecimal("100.00")))
                .getId();

        // Act & Assert
        assertThat(cuentaRepository.existeCuentaDelCliente(cuentaId, null)).isFalse();
        assertThatThrownBy(() -> cuentaService.validarTitularidad(cuentaId, null))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessage("Acceso no autorizado");
    }

    @Test
    @DisplayName("Despues de obtener el id por numero y bloquear en orden se lee el saldo actual de la cuenta destino")
    void despuesDeObtenerIdYBloquearEnOrdenSeLeeElSaldoActualDelDestino() {
        // Arrange
        UUID origenId = cuentaRepository
                .save(new Cuenta(UUID.randomUUID(), "2000000001", TipoCuenta.AHORROS, new BigDecimal("100.00")))
                .getId();
        UUID destinoId = cuentaRepository
                .save(new Cuenta(UUID.randomUUID(), "2000000002", TipoCuenta.AHORROS, new BigDecimal("100.00")))
                .getId();

        TransactionTemplate transaccionA = new TransactionTemplate(transactionManager);
        TransactionTemplate transaccionB = new TransactionTemplate(transactionManager);
        transaccionB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Act
        BigDecimal saldoLeido = transaccionA.execute(estadoA -> {
            UUID idObtenido = cuentaService.obtenerIdPorNumero("2000000002");

            transaccionB.executeWithoutResult(estadoB -> {
                Cuenta cuenta = cuentaRepository.findById(destinoId).orElseThrow();
                cuenta.acreditar(new BigDecimal("30.00"));
                cuentaRepository.save(cuenta);
            });

            cuentaService.bloquearEnOrden(origenId, idObtenido);

            return cuentaRepository.findByIdForUpdate(idObtenido).orElseThrow().getSaldoContable();
        });

        // Assert
        assertThat(saldoLeido).isEqualByComparingTo("130.00");
    }

    @Test
    @DisplayName("Una transferencia mueve los saldos y deja dos filas ligadas por el mismo transferenciaId")
    void transferenciaMueveLosSaldosYDejaDosFilasLigadas() {
        // Arrange
        UUID clienteId = UUID.randomUUID();
        UUID origenId = cuentaRepository
                .save(new Cuenta(clienteId, "2000000003", TipoCuenta.AHORROS, new BigDecimal("100.00")))
                .getId();
        UUID destinoId = cuentaRepository
                .save(new Cuenta(UUID.randomUUID(), "2000000004", TipoCuenta.AHORROS, new BigDecimal("50.00")))
                .getId();
        TransferenciaRequest request = new TransferenciaRequest("2000000004", new BigDecimal("30.00"), "Pago arriendo");

        // Act
        TransferenciaResponse respuesta = transferenciaService.registrarTransferencia(origenId, request, clienteId);

        // Assert
        assertThat(cuentaRepository.findById(origenId).orElseThrow().getSaldoContable())
                .isEqualByComparingTo("70.00");
        assertThat(cuentaRepository.findById(destinoId).orElseThrow().getSaldoContable())
                .isEqualByComparingTo("80.00");

        List<Transaccion> filas = transaccionJpaRepository.findAll().stream()
                .filter(fila -> respuesta.transferenciaId().equals(fila.getTransferenciaId()))
                .toList();
        assertThat(filas)
                .extracting(Transaccion::getCuentaId, Transaccion::getSentido)
                .containsExactlyInAnyOrder(
                        tuple(origenId, SentidoTransferencia.SALIENTE),
                        tuple(destinoId, SentidoTransferencia.ENTRANTE));
        assertThat(filas)
                .filteredOn(fila -> fila.getSentido() == SentidoTransferencia.SALIENTE)
                .extracting(Transaccion::getId)
                .containsExactly(respuesta.id());
    }
}
