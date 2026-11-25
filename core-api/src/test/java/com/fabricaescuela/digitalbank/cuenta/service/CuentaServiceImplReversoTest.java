package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteConsulta;
import com.fabricaescuela.digitalbank.cuenta.dto.MovimientoSaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoHabilitadoException;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoDisponibleException;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.cuenta.exception.SaldoReversoInsuficienteException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CuentaServiceImpl - aplicarReverso (HU-12)")
class CuentaServiceImplReversoTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ID = UUID.randomUUID();
    private static final BigDecimal MONTO = new BigDecimal("30000.00");

    @Mock
    private ICuentaRepository cuentaRepository;

    @Mock
    private IClienteConsulta clienteService;

    @Mock
    private GeneradorNumeroCuenta generadorNumeroCuenta;

    @InjectMocks
    private CuentaServiceImpl cuentaService;

    @Test
    @DisplayName("Reverso crédito acredita la cuenta y devuelve el saldo anterior y nuevo")
    void reversoCreditoAcredita() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("10000.00"));
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(clienteService.estaActivoPorId(CLIENTE_ID)).thenReturn(true);
        when(cuentaRepository.save(cuenta)).thenReturn(cuenta);

        MovimientoSaldoResponse movimiento = cuentaService.aplicarReverso(CUENTA_ID, MONTO, true);

        assertEquals(0, movimiento.saldoAnterior().compareTo(new BigDecimal("10000.00")));
        assertEquals(0, movimiento.saldoNuevo().compareTo(new BigDecimal("40000.00")));
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    @DisplayName("Reverso débito con saldo suficiente debita la cuenta")
    void reversoDebitoDebita() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(clienteService.estaActivoPorId(CLIENTE_ID)).thenReturn(true);
        when(cuentaRepository.save(cuenta)).thenReturn(cuenta);

        MovimientoSaldoResponse movimiento = cuentaService.aplicarReverso(CUENTA_ID, MONTO, false);

        assertEquals(0, movimiento.saldoNuevo().compareTo(new BigDecimal("70000.00")));
    }

    @Test
    @DisplayName("Reverso débito sin saldo suficiente responde 409 'Saldo insuficiente para reversar'")
    void reversoDebitoSinSaldoResponde409() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("10000.00"));
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(clienteService.estaActivoPorId(CLIENTE_ID)).thenReturn(true);

        SaldoReversoInsuficienteException ex = assertThrows(SaldoReversoInsuficienteException.class,
                () -> cuentaService.aplicarReverso(CUENTA_ID, new BigDecimal("50000.00"), false));

        assertEquals("Saldo insuficiente para reversar", ex.getMessage());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reverso sobre una cuenta cerrada responde 409 CUENTA_NO_DISPONIBLE")
    void reversoCuentaCerradaResponde409() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        ReflectionTestUtils.setField(cuenta, "estado", EstadoCuenta.CERRADA);
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        CuentaNoDisponibleException ex = assertThrows(CuentaNoDisponibleException.class,
                () -> cuentaService.aplicarReverso(CUENTA_ID, MONTO, true));

        assertEquals("CUENTA_NO_DISPONIBLE", ex.getErrorCode());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reverso con el cliente de la cuenta inactivo responde 409 CLIENTE_NO_HABILITADO")
    void reversoClienteInactivoResponde409() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(clienteService.estaActivoPorId(CLIENTE_ID)).thenReturn(false);

        ClienteNoHabilitadoException ex = assertThrows(ClienteNoHabilitadoException.class,
                () -> cuentaService.aplicarReverso(CUENTA_ID, MONTO, true));

        assertEquals("CLIENTE_NO_HABILITADO", ex.getErrorCode());
        assertEquals("Cliente no habilitado", ex.getMessage());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reverso sobre una cuenta inexistente responde 404")
    void reversoCuentaInexistenteResponde404() {
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class,
                () -> cuentaService.aplicarReverso(CUENTA_ID, MONTO, true));
    }

    private Cuenta cuentaConSaldo(BigDecimal saldo) {
        Cuenta cuenta = new Cuenta(CLIENTE_ID, "1234567890", TipoCuenta.AHORROS, saldo);
        ReflectionTestUtils.setField(cuenta, "id", CUENTA_ID);
        return cuenta;
    }
}
