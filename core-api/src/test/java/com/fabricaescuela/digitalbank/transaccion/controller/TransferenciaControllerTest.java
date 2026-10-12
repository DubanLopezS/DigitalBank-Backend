package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaResponse;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.ITransferenciaService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferenciaController - registro de transferencias")
class TransferenciaControllerTest {

    @Mock
    private ITransferenciaService transferenciaService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private TransferenciaController transferenciaController;

    @Test
    @DisplayName("La transferencia responde 201 con el comprobante")
    void laTransferenciaResponde201() {
        // Arrange
        UUID cuentaId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        TransferenciaRequest request = new TransferenciaRequest(
                "1234567890", new BigDecimal("30000.00"), "Pago arriendo");
        TransferenciaResponse esperada = new TransferenciaResponse(
                UUID.randomUUID(), UUID.randomUUID(), cuentaId, "1234567890", new BigDecimal("30000.00"),
                "Pago arriendo", "COMPLETADA", LocalDateTime.now());
        when(authentication.getDetails()).thenReturn(clienteId);
        when(transferenciaService.registrarTransferencia(cuentaId, request, clienteId)).thenReturn(esperada);

        // Act
        ResponseEntity<TransferenciaResponse> respuesta =
                transferenciaController.transferir(cuentaId, request, authentication);

        // Assert
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).isEqualTo(esperada);
    }
}
