package com.fabricaescuela.digitalbank.transaccion.dto;

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

@DisplayName("TransferenciaRequest - validaciones de la transferencia")
class TransferenciaRequestValidationTest {

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
    @DisplayName("Una transferencia sin descripcion es valida")
    void transferenciaSinDescripcionEsValida() {
        // Arrange
        TransferenciaRequest request = new TransferenciaRequest("1234567890", new BigDecimal("0.01"), null);

        // Act
        Set<ConstraintViolation<TransferenciaRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones).isEmpty();
    }

    @Test
    @DisplayName("Un monto de cero es rechazado porque el minimo es exclusivo")
    void montoCeroEsRechazado() {
        // Arrange
        TransferenciaRequest request = new TransferenciaRequest("1234567890", BigDecimal.ZERO, null);

        // Act
        Set<ConstraintViolation<TransferenciaRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Monto inválido");
    }

    @Test
    @DisplayName("La cuenta destino y el monto son obligatorios")
    void cuentaDestinoYMontoSonObligatorios() {
        // Arrange
        TransferenciaRequest request = new TransferenciaRequest(" ", null, null);

        // Act
        Set<ConstraintViolation<TransferenciaRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder("La cuenta destino es obligatoria", "El monto es obligatorio");
    }

    @Test
    @DisplayName("Una descripcion de mas de 255 caracteres es rechazada")
    void descripcionDeMasDe255CaracteresEsRechazada() {
        // Arrange
        TransferenciaRequest request = new TransferenciaRequest(
                "1234567890", new BigDecimal("1000.00"), "a".repeat(256));

        // Act
        Set<ConstraintViolation<TransferenciaRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones)
                .extracting(violacion -> violacion.getPropertyPath().toString())
                .containsExactly("descripcion");
    }

    @ParameterizedTest(name = "El monto {0} es rechazado")
    @ValueSource(strings = {"0.004", "10.005", "1500.255"})
    @DisplayName("Un monto con tres decimales es rechazado con Monto invalido")
    void montoConTresDecimalesEsRechazado(String monto) {
        // Arrange
        TransferenciaRequest request = new TransferenciaRequest("1234567890", new BigDecimal(monto), null);

        // Act
        Set<ConstraintViolation<TransferenciaRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Monto inválido");
    }

    @Test
    @DisplayName("Un monto con dos decimales sigue siendo valido")
    void montoConDosDecimalesEsValido() {
        // Arrange
        TransferenciaRequest request = new TransferenciaRequest("1234567890", new BigDecimal("1500.25"), null);

        // Act
        Set<ConstraintViolation<TransferenciaRequest>> violaciones = validator.validate(request);

        // Assert
        assertThat(violaciones).isEmpty();
    }
}
