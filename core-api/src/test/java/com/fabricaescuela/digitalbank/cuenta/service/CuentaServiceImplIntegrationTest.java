package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CuentaServiceImplIntegrationTest {

    @Autowired
    private ICuentaService cuentaService;

    @Autowired
    private ICuentaRepository cuentaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
}
