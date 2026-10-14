package com.fabricaescuela.digitalbank.cuenta.controller;

import com.fabricaescuela.digitalbank.cuenta.dto.CambioEstadoCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CambioEstadoCuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoAccionCuenta;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.IEstadoCuentaService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EstadoCuentaController - bloqueo, desbloqueo y cierre")
class EstadoCuentaControllerTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final String MOTIVO = "Actividad sospechosa detectada";

    @Mock
    private IEstadoCuentaService estadoCuentaService;

    @InjectMocks
    private EstadoCuentaController estadoCuentaController;

    @Test
    @DisplayName("Bloquear responde 200 con el resultado del servicio")
    void bloquearResponde200() {
        
        UUID usuarioId = UUID.randomUUID();
        CambioEstadoCuentaRequest request = new CambioEstadoCuentaRequest(MOTIVO);
        CambioEstadoCuentaResponse esperada = respuesta(TipoAccionCuenta.BLOQUEO,
                EstadoCuenta.ACTIVA, EstadoCuenta.BLOQUEADA, usuarioId);
        when(estadoCuentaService.bloquear(CUENTA_ID, usuarioId, request)).thenReturn(esperada);


        ResponseEntity<CambioEstadoCuentaResponse> resultado =
                estadoCuentaController.bloquear(CUENTA_ID, request, autenticacion(usuarioId));

        
        assertThat(resultado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resultado.getBody()).isEqualTo(esperada);
    }

    @Test
    @DisplayName("Desbloquear responde 200 con el resultado del servicio")
    void desbloquearResponde200() {
        
        UUID usuarioId = UUID.randomUUID();
        CambioEstadoCuentaRequest request = new CambioEstadoCuentaRequest(MOTIVO);
        CambioEstadoCuentaResponse esperada = respuesta(TipoAccionCuenta.DESBLOQUEO,
                EstadoCuenta.BLOQUEADA, EstadoCuenta.ACTIVA, usuarioId);
        when(estadoCuentaService.desbloquear(CUENTA_ID, usuarioId, request)).thenReturn(esperada);

        
        ResponseEntity<CambioEstadoCuentaResponse> resultado =
                estadoCuentaController.desbloquear(CUENTA_ID, request, autenticacion(usuarioId));

        
        assertThat(resultado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resultado.getBody()).isEqualTo(esperada);
    }

    @Test
    @DisplayName("Cerrar responde 200 con el resultado del servicio")
    void cerrarResponde200() {
        
        UUID usuarioId = UUID.randomUUID();
        CambioEstadoCuentaRequest request = new CambioEstadoCuentaRequest(MOTIVO);
        CambioEstadoCuentaResponse esperada = respuesta(TipoAccionCuenta.CIERRE,
                EstadoCuenta.ACTIVA, EstadoCuenta.CERRADA, usuarioId);
        when(estadoCuentaService.cerrar(CUENTA_ID, usuarioId, request)).thenReturn(esperada);

        
        ResponseEntity<CambioEstadoCuentaResponse> resultado =
                estadoCuentaController.cerrar(CUENTA_ID, request, autenticacion(usuarioId));

        
        assertThat(resultado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resultado.getBody()).isEqualTo(esperada);
    }

    @Test
    @DisplayName("El responsable del bloqueo se toma del token, no de la peticion")
    void elResponsableDelBloqueoSeTomaDelToken() {
        
        UUID usuarioId = UUID.randomUUID();
        CambioEstadoCuentaRequest request = new CambioEstadoCuentaRequest(MOTIVO);

        
        estadoCuentaController.bloquear(CUENTA_ID, request, autenticacion(usuarioId));

        
        verify(estadoCuentaService).bloquear(CUENTA_ID, usuarioId, request);
    }

    @Test
    @DisplayName("El responsable del desbloqueo se toma del token, no de la peticion")
    void elResponsableDelDesbloqueoSeTomaDelToken() {
        
        UUID usuarioId = UUID.randomUUID();
        CambioEstadoCuentaRequest request = new CambioEstadoCuentaRequest(MOTIVO);

        
        estadoCuentaController.desbloquear(CUENTA_ID, request, autenticacion(usuarioId));

        
        verify(estadoCuentaService).desbloquear(CUENTA_ID, usuarioId, request);
    }

    @Test
    @DisplayName("El responsable del cierre se toma del token, no de la peticion")
    void elResponsableDelCierreSeTomaDelToken() {
        
        UUID usuarioId = UUID.randomUUID();
        CambioEstadoCuentaRequest request = new CambioEstadoCuentaRequest(MOTIVO);

        
        estadoCuentaController.cerrar(CUENTA_ID, request, autenticacion(usuarioId));


        verify(estadoCuentaService).cerrar(CUENTA_ID, usuarioId, request);
    }

    private Authentication autenticacion(UUID usuarioId) {
        
        return new UsernamePasswordAuthenticationToken(
                usuarioId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private CambioEstadoCuentaResponse respuesta(TipoAccionCuenta accion, EstadoCuenta anterior,
                                                EstadoCuenta nuevo, UUID usuarioId) {
        return new CambioEstadoCuentaResponse("1234567890", accion, anterior, nuevo,
                MOTIVO, LocalDateTime.now(), usuarioId);
    }
}