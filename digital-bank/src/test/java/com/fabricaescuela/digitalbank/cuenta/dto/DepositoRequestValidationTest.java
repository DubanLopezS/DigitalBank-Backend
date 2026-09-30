package com.fabricaescuela.digitalbank.cuenta.dto;

import com.fabricaescuela.digitalbank.cuenta.entity.OrigenDeposito;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DepositoRequest - validaciones del monto")
class DepositoRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void abrirValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        factory.close();
    }

    /**
     * Reproduce HZ-07. La base de datos guarda los montos con dos decimales
     * (NUMERIC(15,2)); un monto con tres decimales se redondea al guardarse y
     * la API responde un valor distinto del guardado. Se espera que se rechace.
     */
    @Test
    @Disabled("HZ-07 abierto: se activa en el Pull Request que corrige el defecto")
    @DisplayName("HZ-07: un monto con tres decimales es rechazado")
    void montoConTresDecimalesEsRechazado() {
        // Arrange
        DepositoRequest request = new DepositoRequest(new BigDecimal("10.555"), OrigenDeposito.EFECTIVO);

        // Act
        Set<ConstraintViolation<DepositoRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones).isNotEmpty();
    }
}
