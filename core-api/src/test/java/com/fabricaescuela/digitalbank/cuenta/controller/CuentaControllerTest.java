package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cuenta.dto.AperturaCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CuentaController - apertura de cuenta")
class CuentaControllerTest {

    @Mock
    private ICuentaService cuentaService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CuentaController cuentaController;

    @Test
    @DisplayName("La apertura responde 201 con la cuenta creada")
    void laAperturaResponde201() {
        // Arrange
        UUID clienteId = UUID.randomUUID();
        CuentaResponse esperada = cuentaResponse(clienteId);
        when(authentication.getDetails()).thenReturn(clienteId);
        when(cuentaService.abrirCuenta(any(AperturaCuentaRequest.class), any(UUID.class))).thenReturn(esperada);

        // Act
        ResponseEntity<CuentaResponse> respuesta =
                cuentaController.abrirCuenta(aperturaRequest(), authentication);

        // Assert
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).isEqualTo(esperada);
    }

    @Test
    @DisplayName("El cliente autenticado se toma de la autenticacion, no del cuerpo de la peticion")
    void elClienteAutenticadoSeTomaDeLaAutenticacion() {
        // Arrange
        UUID clienteIdAutenticado = UUID.randomUUID();
        AperturaCuentaRequest request = aperturaRequest();
        when(authentication.getDetails()).thenReturn(clienteIdAutenticado);
        when(cuentaService.abrirCuenta(request, clienteIdAutenticado)).thenReturn(cuentaResponse(clienteIdAutenticado));

        // Act
        ResponseEntity<CuentaResponse> respuesta = cuentaController.abrirCuenta(request, authentication);

        // Assert: si el controlador usara otro id, el stub no coincidiria y el cuerpo vendria vacio
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().clienteId()).isEqualTo(clienteIdAutenticado);
    }

    private AperturaCuentaRequest aperturaRequest() {
        return new AperturaCuentaRequest(
                TipoDocumento.CC, "1020304050", TipoCuenta.AHORROS, new BigDecimal("150000.00"));
    }

    private CuentaResponse cuentaResponse(UUID clienteId) {
        return new CuentaResponse(
                UUID.randomUUID(), clienteId, "1234567890", TipoCuenta.AHORROS,
                new BigDecimal("150000.00"), BigDecimal.ZERO, new BigDecimal("150000.00"),
                "ACTIVA", LocalDateTime.now());
    }
}