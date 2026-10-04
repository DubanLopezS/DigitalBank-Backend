package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteResumenResponse;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteService;
import com.fabricaescuela.digitalbank.cuenta.dto.AperturaCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.dto.MovimientoSaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoHabilitadoException;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoDisponibleException;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {

    private static final String DOCUMENTO = "1020304050";
    private static final UUID CLIENTE_ID = UUID.randomUUID();
    private static final UUID OTRO_CLIENTE_ID = UUID.randomUUID();
    private static final UUID CUENTA_ID = UUID.randomUUID();

    @Mock
    private ICuentaRepository cuentaRepository;

    @Mock
    private IClienteService clienteService;

    @Mock
    private GeneradorNumeroCuenta generadorNumeroCuenta;

    @InjectMocks
    private CuentaServiceImpl cuentaService;

    @Test
    @DisplayName("Un cliente no puede abrir cuenta a nombre de otro, responde 403")
    void noSePuedeAbrirCuentaANombreDeOtroCliente() {
        when(clienteService.obtenerResumenPorDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.of(new ClienteResumenResponse(CLIENTE_ID, true)));

        ClienteNoAutorizadoException ex = assertThrows(
                ClienteNoAutorizadoException.class,
                () -> cuentaService.abrirCuenta(request(BigDecimal.valueOf(50000)), OTRO_CLIENTE_ID)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC-2: cliente INACTIVO no puede abrir cuenta, responde 403 'Cliente no habilitado'")
    void clienteInactivoNoPuedeAbrirCuenta() {
        when(clienteService.obtenerResumenPorDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.of(new ClienteResumenResponse(CLIENTE_ID, false)));

        ClienteNoHabilitadoException ex = assertThrows(
                ClienteNoHabilitadoException.class,
                () -> cuentaService.abrirCuenta(request(BigDecimal.valueOf(50000)), CLIENTE_ID)
        );

        assertEquals("Cliente no habilitado", ex.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cliente inexistente responde 404 'Cliente no encontrado'")
    void clienteInexistenteRespondeNotFound() {
        when(clienteService.obtenerResumenPorDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.empty());

        ClienteNoEncontradoException ex = assertThrows(
                ClienteNoEncontradoException.class,
                () -> cuentaService.abrirCuenta(request(null), CLIENTE_ID)
        );

        assertEquals("Cliente no encontrado", ex.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC-4: sin monto de apertura la cuenta inicia en 0")
    void sinMontoLaCuentaIniciaEnCero() {
        prepararClienteActivo();

        CuentaResponse response = cuentaService.abrirCuenta(request(null), CLIENTE_ID);

        assertEquals(0, response.saldoContable().compareTo(BigDecimal.ZERO));
        assertEquals(0, response.saldoDisponible().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("AC-3 y AC-5: la cuenta se crea ACTIVA, con numero de 10 digitos y el saldo indicado")
    void cuentaSeCreaActivaConNumeroDeDiezDigitos() {
        prepararClienteActivo();

        CuentaResponse response = cuentaService.abrirCuenta(request(BigDecimal.valueOf(150000)), CLIENTE_ID);

        assertEquals("ACTIVA", response.estado());
        assertEquals(10, response.numeroCuenta().length());
        assertEquals(TipoCuenta.AHORROS, response.tipoCuenta());
        assertEquals(0, response.saldoContable().compareTo(BigDecimal.valueOf(150000)));
        assertEquals(0, response.retencion().compareTo(BigDecimal.ZERO));
        assertNotNull(response.fechaApertura());
    }

    @Test
    @DisplayName("Acreditar en una cuenta inexistente responde 404 'Cuenta no encontrada'")
    void acreditarEnCuentaInexistenteRespondeNotFound() {
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.empty());

        CuentaNoEncontradaException ex = assertThrows(
                CuentaNoEncontradaException.class,
                () -> cuentaService.acreditar(CUENTA_ID, new BigDecimal("50000.00"))
        );

        assertEquals("Cuenta no encontrada", ex.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Acreditar en una cuenta CERRADA responde 409 y no altera el saldo")
    void acreditarEnCuentaCerradaRespondeConflict() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        ReflectionTestUtils.setField(cuenta, "estado", EstadoCuenta.CERRADA);
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        CuentaNoDisponibleException ex = assertThrows(
                CuentaNoDisponibleException.class,
                () -> cuentaService.acreditar(CUENTA_ID, new BigDecimal("50000.00"))
        );

        assertEquals("Cuenta no disponible", ex.getMessage());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(0, cuenta.getSaldoContable().compareTo(new BigDecimal("100000.00")));
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Acreditar suma el monto, guarda la cuenta y devuelve el saldo anterior y el nuevo")
    void acreditarDevuelveSaldoAnteriorYNuevo() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.save(cuenta)).thenReturn(cuenta);

        MovimientoSaldoResponse movimiento = cuentaService.acreditar(CUENTA_ID, new BigDecimal("50000.00"));

        assertEquals(CUENTA_ID, movimiento.cuentaId());
        assertEquals(0, movimiento.saldoAnterior().compareTo(new BigDecimal("100000.00")));
        assertEquals(0, movimiento.saldoNuevo().compareTo(new BigDecimal("150000.00")));
        assertEquals(0, cuenta.getSaldoContable().compareTo(new BigDecimal("150000.00")));
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    @DisplayName("Una cuenta BLOQUEADA si admite acreditaciones porque no esta cerrada")
    void cuentaBloqueadaAdmiteAcreditaciones() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        ReflectionTestUtils.setField(cuenta, "estado", EstadoCuenta.BLOQUEADA);
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.save(cuenta)).thenReturn(cuenta);

        MovimientoSaldoResponse movimiento = cuentaService.acreditar(CUENTA_ID, new BigDecimal("1000.00"));

        assertEquals(0, movimiento.saldoNuevo().compareTo(new BigDecimal("101000.00")));
    }

    private Cuenta cuentaConSaldo(BigDecimal saldo) {
        Cuenta cuenta = new Cuenta(CLIENTE_ID, "1234567890", TipoCuenta.AHORROS, saldo);
        ReflectionTestUtils.setField(cuenta, "id", CUENTA_ID);
        return cuenta;
    }

    private void prepararClienteActivo() {
        when(clienteService.obtenerResumenPorDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.of(new ClienteResumenResponse(CLIENTE_ID, true)));
        when(generadorNumeroCuenta.generar()).thenReturn("1234567890");
        when(cuentaRepository.save(any(Cuenta.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AperturaCuentaRequest request(BigDecimal monto) {
        return new AperturaCuentaRequest(TipoDocumento.CC, DOCUMENTO, TipoCuenta.AHORROS, monto);
    }
}