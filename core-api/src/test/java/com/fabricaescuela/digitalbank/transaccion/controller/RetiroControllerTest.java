package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.transaccion.dto.RetiroRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IRetiroService;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetiroController - registro de retiros")
class RetiroControllerTest {

    @Mock
    private IRetiroService retiroService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private RetiroController retiroController;

    @Test
    @DisplayName("El retiro responde 201 con la transaccion registrada")
    void elRetiroResponde201() {
        // Arrange
        UUID cuentaId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        RetiroRequest request = new RetiroRequest(new BigDecimal("30000.00"));
        TransaccionResponse esperada = new TransaccionResponse(
                UUID.randomUUID(), cuentaId, "RETIRO", new BigDecimal("30000.00"),
                new BigDecimal("100000.00"), new BigDecimal("70000.00"), null, "COMPLETADA", LocalDateTime.now());
        when(authentication.getDetails()).thenReturn(clienteId);
        when(retiroService.registrarRetiro(cuentaId, request, clienteId)).thenReturn(esperada);

        // Act
        ResponseEntity<TransaccionResponse> respuesta = retiroController.retirar(cuentaId, request, authentication);

        // Assert
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).isEqualTo(esperada);
    }

    @Test
    @DisplayName("El cliente se pasa tal cual al servicio, aunque venga nulo")
    void elClienteSeTomaDeLaAutenticacionSinRevisarlo() {
        // Arrange
        UUID cuentaId = UUID.randomUUID();
        RetiroRequest request = new RetiroRequest(new BigDecimal("30000.00"));
        when(authentication.getDetails()).thenReturn(null);

        // Act
        retiroController.retirar(cuentaId, request, authentication);

        // Assert
        verify(retiroService).registrarRetiro(cuentaId, request, null);
    }
}
