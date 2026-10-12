package com.fabricaescuela.digitalbank.transaccion.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RetiroRequest - validaciones del monto")
class RetiroRequestValidationTest {

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

    @Test
    @DisplayName("Un monto positivo es valido")
    void montoPositivoEsValido() {
        // Arrange
        RetiroRequest request = new RetiroRequest(new BigDecimal("0.01"));

        // Act
        Set<ConstraintViolation<RetiroRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones).isEmpty();
    }

    @Test
    @DisplayName("Un monto de cero es rechazado porque el minimo es exclusivo")
    void montoCeroEsRechazado() {
        // Arrange
        RetiroRequest request = new RetiroRequest(BigDecimal.ZERO);

        // Act
        Set<ConstraintViolation<RetiroRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Monto inválido");
    }

    @Test
    @DisplayName("Un monto negativo es rechazado")
    void montoNegativoEsRechazado() {
        // Arrange
        RetiroRequest request = new RetiroRequest(new BigDecimal("-1000"));

        // Act
        Set<ConstraintViolation<RetiroRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Monto inválido");
    }

    @Test
    @DisplayName("El monto es obligatorio")
    void montoEsObligatorio() {
        // Arrange
        RetiroRequest request = new RetiroRequest(null);

        // Act
        Set<ConstraintViolation<RetiroRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones)
                .extracting(violacion -> violacion.getPropertyPath().toString())
                .containsExactly("monto");
    }
}
