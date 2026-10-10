package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.cuenta.dto.SaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ISaldoService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SaldoController - traduccion del contexto de seguridad")
class SaldoControllerTest {

    private static final String NUMERO_CUENTA = "1234567890";

    @Mock
    private ISaldoService saldoService;

    @Captor
    private ArgumentCaptor<String> rolCaptor;

    @InjectMocks
    private SaldoController saldoController;

    @Test
    @DisplayName("La consulta responde 200 con el saldo de la cuenta")
    void laConsultaResponde200ConElSaldo() {
        SaldoResponse esperado = new SaldoResponse(
                NUMERO_CUENTA, new BigDecimal("200000.00"), new BigDecimal("170000.00"));
        when(saldoService.consultarSaldo(anyString(), any(UUID.class), anyString())).thenReturn(esperado);

        ResponseEntity<SaldoResponse> respuesta = saldoController.consultarSaldo(
                NUMERO_CUENTA, autenticacion(UUID.randomUUID(), UUID.randomUUID(), "ROLE_CLIENTE"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("El prefijo ROLE_ se elimina antes de delegar al servicio")
    void elPrefijoRoleSeEliminaAntesDeDelegar() {
        UUID clienteId = UUID.randomUUID();
        Authentication autenticacion = autenticacion(UUID.randomUUID(), clienteId, "ROLE_CAJERO");

        saldoController.consultarSaldo(NUMERO_CUENTA, autenticacion);

        verify(saldoService).consultarSaldo(anyString(), any(UUID.class), rolCaptor.capture());
        assertThat(rolCaptor.getValue()).isEqualTo("CAJERO");
    }

    @Test
    @DisplayName("El clienteId se toma de los detalles de la autenticacion")
    void elClienteIdSeTomaDeLosDetalles() {
        UUID clienteId = UUID.randomUUID();

        saldoController.consultarSaldo(NUMERO_CUENTA, autenticacion(UUID.randomUUID(), clienteId, "ROLE_CLIENTE"));

        verify(saldoService).consultarSaldo(NUMERO_CUENTA, clienteId, "CLIENTE");
    }

    @Test
    @DisplayName("Una autenticacion sin roles no llega a consultar el saldo")
    void autenticacionSinRolesNoConsultaElSaldo() {
        Authentication sinRoles = new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, List.of());

        assertThatThrownBy(() -> saldoController.consultarSaldo(NUMERO_CUENTA, sinRoles))
                .isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(saldoService);
    }

    private Authentication autenticacion(UUID usuarioId, UUID clienteId, String authority) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                usuarioId, null, List.of(new SimpleGrantedAuthority(authority)));
        token.setDetails(clienteId);
        return token;
    }
}