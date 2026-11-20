package com.fabricaescuela.digitalbank.transaccion.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReversoRequest - validacion del motivo")
class ReversoRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Un motivo de 10 caracteres es valido")
    void motivoDeDiezCaracteresEsValido() {
        Set<ConstraintViolation<ReversoRequest>> errores =
                validator.validate(new ReversoRequest("1234567890"));

        assertThat(errores).isEmpty();
    }

    @Test
    @DisplayName("Un motivo de 9 caracteres es invalido")
    void motivoDeNueveCaracteresEsInvalido() {
        Set<ConstraintViolation<ReversoRequest>> errores =
                validator.validate(new ReversoRequest("123456789"));

        assertThat(errores).hasSize(1);
    }

    @Test
    @DisplayName("Un motivo nulo es invalido")
    void motivoNuloEsInvalido() {
        Set<ConstraintViolation<ReversoRequest>> errores =
                validator.validate(new ReversoRequest(null));

        assertThat(errores).isNotEmpty();
    }

    @Test
    @DisplayName("Un motivo en blanco es invalido")
    void motivoEnBlancoEsInvalido() {
        Set<ConstraintViolation<ReversoRequest>> errores =
                validator.validate(new ReversoRequest("            "));

        assertThat(errores).isNotEmpty();
    }

    @Test
    @DisplayName("Los espacios de los extremos no cuentan para el minimo de 10 caracteres")
    void losEspaciosDeLosExtremosNoCuentan() {
        ReversoRequest request = new ReversoRequest("     abcde     ");

        Set<ConstraintViolation<ReversoRequest>> errores = validator.validate(request);

        assertThat(request.motivo()).isEqualTo("abcde");
        assertThat(errores).isNotEmpty();
    }

    @Test
    @DisplayName("Un motivo valido se guarda sin los espacios de los extremos")
    void elMotivoValidoSeGuardaRecortado() {
        ReversoRequest request = new ReversoRequest("  Deposito duplicado  ");

        assertThat(request.motivo()).isEqualTo("Deposito duplicado");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("Un motivo de mas de 255 caracteres es invalido")
    void motivoMuyLargoEsInvalido() {
        Set<ConstraintViolation<ReversoRequest>> errores =
                validator.validate(new ReversoRequest("a".repeat(256)));

        assertThat(errores).hasSize(1);
    }
}
