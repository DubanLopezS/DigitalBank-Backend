package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResumenResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.IListadoCuentasService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClienteCuentasController - listado de cuentas")
class ClienteCuentasControllerTest {

    private static final UUID CLIENTE_ID = UUID.randomUUID();

    @Mock
    private IListadoCuentasService listadoCuentasService;

    @InjectMocks
    private ClienteCuentasController controller;

    @Test
    @DisplayName("Un CLIENTE recibe 200 y su clienteId se toma del token")
    void clienteRecibe200() {
        // Arrange

        List<CuentaResumenResponse> esperada = List.of(new CuentaResumenResponse(
        "1234567890", TipoCuenta.AHORROS, new BigDecimal("50.00"), EstadoCuenta.ACTIVA));
        when(listadoCuentasService.listar(CLIENTE_ID, "ACTIVA", CLIENTE_ID)).thenReturn(esperada);

        // Act
        ResponseEntity<List<CuentaResumenResponse>> resultado =
                controller.listar(CLIENTE_ID, "ACTIVA", autenticacion("ROLE_CLIENTE", CLIENTE_ID));

        // Assert
        assertThat(resultado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resultado.getBody()).isEqualTo(esperada);
    }

    @Test
    @DisplayName("Un ADMIN no tiene clienteId en el token y el service recibe null")
    void adminPasaClienteIdNulo() {
        // Arrange
        when(listadoCuentasService.listar(CLIENTE_ID, null, null)).thenReturn(List.of());

        // Act
        ResponseEntity<List<CuentaResumenResponse>> resultado =
                controller.listar(CLIENTE_ID, null, autenticacion("ROLE_ADMIN", null));

        // Assert
        assertThat(resultado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resultado.getBody()).isEmpty();
        verify(listadoCuentasService).listar(CLIENTE_ID, null, null);
    }

    private Authentication autenticacion(String rol, UUID clienteId) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(), null, List.of(new SimpleGrantedAuthority(rol)));
        token.setDetails(clienteId);
        return token;
    }
}