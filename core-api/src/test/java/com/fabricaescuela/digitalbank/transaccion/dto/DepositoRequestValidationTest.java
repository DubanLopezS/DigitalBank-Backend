package com.fabricaescuela.digitalbank.transaccion.dto;

import com.fabricaescuela.digitalbank.transaccion.entity.OrigenDeposito;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DepositoRequest - validaciones del monto y el origen")
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

    @Test
    @DisplayName("Un monto positivo con origen definido es valido")
    void montoPositivoConOrigenEsValido() {
        // Arrange
        DepositoRequest request = new DepositoRequest(new BigDecimal("0.01"), OrigenDeposito.EFECTIVO);

        // Act
        Set<ConstraintViolation<DepositoRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones).isEmpty();
    }

    @Test
    @DisplayName("Un monto de cero es rechazado porque el minimo es exclusivo")
    void montoCeroEsRechazado() {
        // Arrange
        DepositoRequest request = new DepositoRequest(BigDecimal.ZERO, OrigenDeposito.EFECTIVO);

        // Act
        Set<ConstraintViolation<DepositoRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Monto inválido");
    }

    @Test
    @DisplayName("Un monto negativo es rechazado")
    void montoNegativoEsRechazado() {
        // Arrange
        DepositoRequest request = new DepositoRequest(new BigDecimal("-1000"), OrigenDeposito.EFECTIVO);

        // Act
        Set<ConstraintViolation<DepositoRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones)
                .extracting(violacion -> violacion.getPropertyPath().toString())
                .containsExactly("monto");
    }

    @Test
    @DisplayName("El monto y el origen son obligatorios")
    void montoYOrigenSonObligatorios() {
        // Arrange
        DepositoRequest request = new DepositoRequest(null, null);

        // Act
        Set<ConstraintViolation<DepositoRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones)
                .extracting(violacion -> violacion.getPropertyPath().toString())
                .containsExactlyInAnyOrder("monto", "origen");
    }

    @ParameterizedTest(name = "El monto {0} es rechazado")
    @ValueSource(strings = {"0.004", "10.005", "1500.255"})
    @DisplayName("Un monto con tres decimales es rechazado con Monto invalido")
    void montoConTresDecimalesEsRechazado(String monto) {
        // Arrange
        DepositoRequest request = new DepositoRequest(new BigDecimal(monto), OrigenDeposito.EFECTIVO);

        // Act
        Set<ConstraintViolation<DepositoRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Monto inválido");
    }

    @Test
    @DisplayName("Un monto con dos decimales sigue siendo valido")
    void montoConDosDecimalesEsValido() {
        // Arrange
        DepositoRequest request = new DepositoRequest(new BigDecimal("1500.25"), OrigenDeposito.EFECTIVO);

        // Act
        Set<ConstraintViolation<DepositoRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones).isEmpty();
    }

    @Test
    @DisplayName("Un monto con mas de 13 digitos enteros no cabe en NUMERIC(15,2) y es rechazado")
    void montoConMasDeTreceEnterosEsRechazado() {
        // Arrange
        DepositoRequest request = new DepositoRequest(new BigDecimal("12345678901234.00"), OrigenDeposito.EFECTIVO);

        // Act
        Set<ConstraintViolation<DepositoRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Monto inválido");
    }
}
