package com.fabricaescuela.digitalbank.cuenta.dto;


import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CambioEstadoCuentaRequest - validacion del motivo")
class CambioEstadoCuentaRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Un motivo de 10 caracteres es valido")
    void motivoDeDiezCaracteresEsValido() {

        Set<ConstraintViolation<CambioEstadoCuentaRequest>> errores =
                validator.validate(new CambioEstadoCuentaRequest("1234567890"));


        assertThat(errores).isEmpty();
    }

    @Test
    @DisplayName("Un motivo de 9 caracteres es invalido")
    void motivoDeNueveCaracteresEsInvalido() {

        Set<ConstraintViolation<CambioEstadoCuentaRequest>> errores =
                validator.validate(new CambioEstadoCuentaRequest("123456789"));


        assertThat(errores).hasSize(1);
    }

    @Test
    @DisplayName("Un motivo nulo es invalido")
    void motivoNuloEsInvalido() {

        Set<ConstraintViolation<CambioEstadoCuentaRequest>> errores =
                validator.validate(new CambioEstadoCuentaRequest(null));


        assertThat(errores).isNotEmpty();
    }

    @Test
    @DisplayName("Un motivo en blanco es invalido")
    void motivoEnBlancoEsInvalido() {

        Set<ConstraintViolation<CambioEstadoCuentaRequest>> errores =
                validator.validate(new CambioEstadoCuentaRequest("            "));


        assertThat(errores).isNotEmpty();
    }

    @Test
    @DisplayName("Los espacios de los extremos no cuentan para el minimo de 10 caracteres")
    void losEspaciosDeLosExtremosNoCuentan() {

        CambioEstadoCuentaRequest request = new CambioEstadoCuentaRequest("     abcde     ");


        Set<ConstraintViolation<CambioEstadoCuentaRequest>> errores = validator.validate(request);

        assertThat(request.motivo()).isEqualTo("abcde");
        assertThat(errores).isNotEmpty();
    }

    @Test
    @DisplayName("Un motivo valido se guarda sin los espacios de los extremos")
    void elMotivoValidoSeGuardaRecortado() {

        CambioEstadoCuentaRequest request = new CambioEstadoCuentaRequest("  Actividad sospechosa  ");


        assertThat(request.motivo()).isEqualTo("Actividad sospechosa");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("Un motivo de mas de 255 caracteres es invalido")
    void motivoMuyLargoEsInvalido() {

        Set<ConstraintViolation<CambioEstadoCuentaRequest>> errores =
                validator.validate(new CambioEstadoCuentaRequest("a".repeat(256)));


        assertThat(errores).hasSize(1);
    }
}