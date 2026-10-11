package com.fabricaescuela.digitalbank.transaccion.dto;

import com.fabricaescuela.digitalbank.transaccion.entity.OrigenDeposito;
import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TransaccionResponse - proyeccion de la transaccion")
class TransaccionResponseTest {

    @Test
    @DisplayName("Un deposito expone su origen como texto")
    void depositoExponeElOrigenComoTexto() {
        // Arrange
        Transaccion deposito = Transaccion.deposito(
                UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, OrigenDeposito.CHEQUE);

        // Act
        TransaccionResponse response = TransaccionResponse.from(deposito);

        // Assert
        assertThat(response.tipo()).isEqualTo("DEPOSITO");
        assertThat(response.origen()).isEqualTo("CHEQUE");
    }

    @Test
    @DisplayName("Un retiro se expone sin origen")
    void retiroSeExponeSinOrigen() {
        // Arrange
        Transaccion retiro = Transaccion.retiro(
                UUID.randomUUID(), new BigDecimal("30000.00"), new BigDecimal("100000.00"), new BigDecimal("70000.00"));

        // Act
        TransaccionResponse response = TransaccionResponse.from(retiro);

        // Assert
        assertThat(response.tipo()).isEqualTo("RETIRO");
        assertThat(response.origen()).isNull();
    }
}
