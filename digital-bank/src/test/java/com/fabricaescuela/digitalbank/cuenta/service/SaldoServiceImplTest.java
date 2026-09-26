package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.auth.interfaces.UsuarioQueryService;
import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.dto.SaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.cuenta.repository.CuentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaldoServiceImplTest {

    private static final String NUMERO_CUENTA = "1234567890";
    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ID = UUID.randomUUID();
    private static final UUID OTRO_CLIENTE_ID = UUID.randomUUID();

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private UsuarioQueryService usuarioQueryService;

    @InjectMocks
    private SaldoServiceImpl saldoService;

    @Test
    @DisplayName("El titular de la cuenta obtiene el saldo contable y el disponible")
    void consultarSaldo_titularDeLaCuenta_debeRetornarSaldoContableYDisponible() {
        Cuenta cuenta = mock(Cuenta.class);
        when(cuenta.getNumeroCuenta()).thenReturn(NUMERO_CUENTA);
        when(cuenta.getSaldoContable()).thenReturn(BigDecimal.valueOf(100000));
        when(cuenta.getRetencion()).thenReturn(BigDecimal.ZERO);
        when(cuenta.getClienteId()).thenReturn(CLIENTE_ID);
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuenta));
        when(usuarioQueryService.obtenerClienteId(USUARIO_ID)).thenReturn(Optional.of(CLIENTE_ID));

        SaldoResponse response = saldoService.consultarSaldo(NUMERO_CUENTA, USUARIO_ID, "CLIENTE");

        assertEquals(NUMERO_CUENTA, response.numeroCuenta());
        assertEquals(0, response.saldoContable().compareTo(BigDecimal.valueOf(100000)));
        assertEquals(0, response.saldoDisponible().compareTo(BigDecimal.valueOf(100000)));
    }

    @Test
    @DisplayName("Con retencion vigente el saldo disponible descuenta la retencion del saldo contable")
    void consultarSaldo_conRetencionVigente_debeCalcularSaldoDisponibleCorrectamente() {
        Cuenta cuenta = mock(Cuenta.class);
        when(cuenta.getNumeroCuenta()).thenReturn(NUMERO_CUENTA);
        when(cuenta.getSaldoContable()).thenReturn(BigDecimal.valueOf(100000));
        when(cuenta.getRetencion()).thenReturn(BigDecimal.valueOf(30000));
        when(cuenta.getClienteId()).thenReturn(CLIENTE_ID);
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuenta));
        when(usuarioQueryService.obtenerClienteId(USUARIO_ID)).thenReturn(Optional.of(CLIENTE_ID));

        SaldoResponse response = saldoService.consultarSaldo(NUMERO_CUENTA, USUARIO_ID, "CLIENTE");

        assertEquals(0, response.saldoContable().compareTo(BigDecimal.valueOf(100000)));
        assertEquals(0, response.saldoDisponible().compareTo(BigDecimal.valueOf(70000)));
    }

    @Test
    @DisplayName("Consultar una cuenta de otro cliente responde con mensaje generico de acceso no autorizado")
    void consultarSaldo_cuentaAjena_debeRechazarConMensajeGenerico() {
        Cuenta cuenta = mock(Cuenta.class);
        when(cuenta.getClienteId()).thenReturn(OTRO_CLIENTE_ID);
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuenta));
        when(usuarioQueryService.obtenerClienteId(USUARIO_ID)).thenReturn(Optional.of(CLIENTE_ID));

        AccesoNoAutorizadoException ex = assertThrows(
                AccesoNoAutorizadoException.class,
                () -> saldoService.consultarSaldo(NUMERO_CUENTA, USUARIO_ID, "CLIENTE")
        );

        assertEquals("Acceso no autorizado", ex.getMessage());
    }

    @Test
    @DisplayName("Una cuenta inexistente responde con el mismo mensaje que una cuenta ajena, para no revelar cuales cuentas existen")
    void consultarSaldo_cuentaInexistente_debeRechazarConElMismoMensajeQueCuentaAjena() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.empty());

        AccesoNoAutorizadoException ex = assertThrows(
                AccesoNoAutorizadoException.class,
                () -> saldoService.consultarSaldo(NUMERO_CUENTA, USUARIO_ID, "CLIENTE")
        );

        assertEquals("Acceso no autorizado", ex.getMessage());
        verifyNoInteractions(usuarioQueryService);
    }

    @Test
    @DisplayName("Para personal autorizado (ADMIN/CAJERO) una cuenta inexistente si responde CuentaNoEncontradaException")
    void consultarSaldo_cuentaInexistentePersonalAutorizado_debeResponderCuentaNoEncontrada() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.empty());

        CuentaNoEncontradaException ex = assertThrows(
                CuentaNoEncontradaException.class,
                () -> saldoService.consultarSaldo(NUMERO_CUENTA, USUARIO_ID, "ADMIN")
        );

        assertEquals("Cuenta no encontrada", ex.getMessage());
        verifyNoInteractions(usuarioQueryService);
    }
}
