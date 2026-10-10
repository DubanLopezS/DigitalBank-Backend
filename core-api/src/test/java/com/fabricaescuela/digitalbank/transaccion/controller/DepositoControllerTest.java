package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.transaccion.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.entity.OrigenDeposito;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IDepositoService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepositoController - registro de depositos")
class DepositoControllerTest {

    @Mock
    private IDepositoService depositoService;

    @InjectMocks
    private DepositoController depositoController;

    @Test
    @DisplayName("El deposito responde 201 con la transaccion registrada")
    void elDepositoResponde201() {
        // Arrange
        UUID cuentaId = UUID.randomUUID();
        DepositoRequest request = new DepositoRequest(new BigDecimal("50000.00"), OrigenDeposito.EFECTIVO);
        TransaccionResponse esperada = new TransaccionResponse(
                UUID.randomUUID(), cuentaId, "DEPOSITO", new BigDecimal("50000.00"),
                BigDecimal.ZERO, new BigDecimal("50000.00"), "EFECTIVO", "COMPLETADA", LocalDateTime.now());
        when(depositoService.registrarDeposito(cuentaId, request)).thenReturn(esperada);

        // Act
        ResponseEntity<TransaccionResponse> respuesta = depositoController.depositar(cuentaId, request);

        // Assert
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).isEqualTo(esperada);
    }
}
