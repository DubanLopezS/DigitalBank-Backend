package com.fabricaescuela.digitalbank.cliente.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica las restricciones del payload de actualización, que son
 * las que producen el 400 antes de llegar al servicio.
 */
@DisplayName("ClienteActualizacionRequest - validaciones del payload")
class ClienteActualizacionRequestValidationTest {

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
    @DisplayName("Un correo y un telefono validos no producen violaciones")
    void payloadCompletoNoProduceViolaciones() {
        // Act
        Set<ConstraintViolation<ClienteActualizacionRequest>> violaciones =
                validator.validate(new ClienteActualizacionRequest("ana@banco.com", "3001234567"));

        // Assert
        assertThat(violaciones).isEmpty();
    }

    @Test
    @DisplayName("Solo el correo o solo el telefono son payloads validos")
    void unSoloCampoEsValido() {
        // Act + Assert
        assertThat(validator.validate(new ClienteActualizacionRequest("ana@banco.com", null))).isEmpty();
        assertThat(validator.validate(new ClienteActualizacionRequest(null, "3001234567"))).isEmpty();
    }

    @Test
    @DisplayName("Un correo sin arroba se rechaza con el mensaje Formato de correo invalido")
    void correoInvalidoEsRechazado() {
        // Act
        Set<ConstraintViolation<ClienteActualizacionRequest>> violaciones =
                validator.validate(new ClienteActualizacionRequest("ana-banco.com", null));

        // Assert
        assertThat(violaciones)
                .extracting(violacion -> violacion.getPropertyPath().toString())
                .containsExactly("email");
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Formato de correo inválido");
    }

    @Test
    @DisplayName("Un correo vacio se rechaza con el mensaje Formato de correo invalido")
    void correoVacioEsRechazado() {
        // Act
        Set<ConstraintViolation<ClienteActualizacionRequest>> violaciones =
                validator.validate(new ClienteActualizacionRequest("", null));

        // Assert
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Formato de correo inválido");
    }

    @Test
    @DisplayName("Un correo valido de 150 caracteres se acepta")
    void correoDe150CaracteresSeAcepta() {
        // Arrange: 60 + 1 + 89 = 150 caracteres
        String email = "a".repeat(60) + "@" + "b".repeat(40) + "." + "c".repeat(44) + ".com";
        assertThat(email).hasSize(150);

        // Act
        Set<ConstraintViolation<ClienteActualizacionRequest>> violaciones =
                validator.validate(new ClienteActualizacionRequest(email, null));

        // Assert
        assertThat(violaciones).isEmpty();
    }

    @Test
    @DisplayName("Un correo valido de 151 caracteres se rechaza por longitud maxima")
    void correoDe151CaracteresEsRechazado() {
        // Arrange: 60 + 1 + 90 = 151 caracteres
        String email = "a".repeat(60) + "@" + "b".repeat(40) + "." + "c".repeat(45) + ".com";
        assertThat(email).hasSize(151);

        // Act
        Set<ConstraintViolation<ClienteActualizacionRequest>> violaciones =
                validator.validate(new ClienteActualizacionRequest(email, null));

        // Assert
        assertThat(violaciones)
                .extracting(violacion -> violacion.getPropertyPath().toString())
                .containsExactly("email");
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("El correo no puede superar 150 caracteres");
    }

    @Test
    @DisplayName("Un telefono de 9 digitos se rechaza por el patron de 10 digitos")
    void telefonoInvalidoEsRechazado() {
        // Act
        Set<ConstraintViolation<ClienteActualizacionRequest>> violaciones =
                validator.validate(new ClienteActualizacionRequest(null, "300123456"));

        // Assert
        assertThat(violaciones)
                .extracting(violacion -> violacion.getPropertyPath().toString())
                .containsExactly("telefono");
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("El teléfono debe tener 10 dígitos");
    }

    @Test
    @DisplayName("Un payload sin correo ni telefono se rechaza")
    void payloadSinCorreoNiTelefonoEsRechazado() {
        // Act
        Set<ConstraintViolation<ClienteActualizacionRequest>> violaciones =
                validator.validate(new ClienteActualizacionRequest(null, null));

        // Assert
        assertThat(violaciones)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Debe enviar el correo o el teléfono");
    }
}
