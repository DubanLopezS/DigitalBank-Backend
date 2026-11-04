package com.fabricaescuela.digitalbank.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NormalizadorCorreo - forma unica del correo")
class NormalizadorCorreoTest {

    @ParameterizedTest(name = "\"{0}\" queda como ana@x.test")
    @ValueSource(strings = {"ana@x.test", "ANA@X.TEST", "Ana@X.Test", "  ana@x.test  ", "\tANA@x.TEST\n"})
    @DisplayName("Quita los espacios de los lados y pasa a minusculas")
    void quitaEspaciosYPasaAMinusculas(String correo) {
        assertThat(NormalizadorCorreo.normalizar(correo)).isEqualTo("ana@x.test");
    }

    @Test
    @DisplayName("Un correo nulo se mantiene nulo para que lo rechace la validacion")
    void correoNuloSeMantieneNulo() {
        assertThat(NormalizadorCorreo.normalizar(null)).isNull();
    }
}
