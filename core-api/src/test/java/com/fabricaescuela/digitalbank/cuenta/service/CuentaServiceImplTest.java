package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteResumenResponse;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteConsulta;
import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
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
import com.fabricaescuela.digitalbank.cuenta.exception.SaldoInsuficienteException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
    private IClienteConsulta clienteService;

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
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.empty());

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
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));

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
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
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
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.save(cuenta)).thenReturn(cuenta);

        MovimientoSaldoResponse movimiento = cuentaService.acreditar(CUENTA_ID, new BigDecimal("1000.00"));

        assertEquals(0, movimiento.saldoNuevo().compareTo(new BigDecimal("101000.00")));
    }

    @Test
    @DisplayName("Acreditar un monto cero o negativo lanza IllegalArgumentException sin tocar el repositorio")
    void acreditarConMontoNoPositivoLanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> cuentaService.acreditar(CUENTA_ID, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> cuentaService.acreditar(CUENTA_ID, new BigDecimal("-100.00")));

        verifyNoInteractions(cuentaRepository);
    }

    @Test
    @DisplayName("Debitar resta el monto, guarda la cuenta y devuelve el saldo anterior y el nuevo")
    void debitarDevuelveSaldoAnteriorYNuevo() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.save(cuenta)).thenReturn(cuenta);

        MovimientoSaldoResponse movimiento = cuentaService.debitar(CUENTA_ID, new BigDecimal("30000.00"));

        assertEquals(CUENTA_ID, movimiento.cuentaId());
        assertEquals(0, movimiento.saldoAnterior().compareTo(new BigDecimal("100000.00")));
        assertEquals(0, movimiento.saldoNuevo().compareTo(new BigDecimal("70000.00")));
        assertEquals(0, cuenta.getSaldoContable().compareTo(new BigDecimal("70000.00")));
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    @DisplayName("Debitar en una cuenta inexistente responde 404 'Cuenta no encontrada'")
    void debitarEnCuentaInexistenteRespondeNotFound() {
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.empty());

        CuentaNoEncontradaException ex = assertThrows(
                CuentaNoEncontradaException.class,
                () -> cuentaService.debitar(CUENTA_ID, new BigDecimal("50000.00"))
        );

        assertEquals("Cuenta no encontrada", ex.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debitar en una cuenta CERRADA responde 409 y no altera el saldo")
    void debitarEnCuentaCerradaRespondeConflict() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        ReflectionTestUtils.setField(cuenta, "estado", EstadoCuenta.CERRADA);
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        CuentaNoDisponibleException ex = assertThrows(
                CuentaNoDisponibleException.class,
                () -> cuentaService.debitar(CUENTA_ID, new BigDecimal("50000.00"))
        );

        assertEquals("Cuenta no disponible", ex.getMessage());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(0, cuenta.getSaldoContable().compareTo(new BigDecimal("100000.00")));
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debitar en una cuenta BLOQUEADA responde 409 y no guarda nada")
    void debitarEnCuentaBloqueadaRespondeConflict() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        ReflectionTestUtils.setField(cuenta, "estado", EstadoCuenta.BLOQUEADA);
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        CuentaNoDisponibleException ex = assertThrows(
                CuentaNoDisponibleException.class,
                () -> cuentaService.debitar(CUENTA_ID, new BigDecimal("50000.00"))
        );

        assertEquals("Cuenta no disponible", ex.getMessage());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(0, cuenta.getSaldoContable().compareTo(new BigDecimal("100000.00")));
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debitar mas de lo disponible responde 409 'Saldo insuficiente' y no guarda nada")
    void debitarConSaldoInsuficienteRespondeConflict() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("10000.00"));
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        SaldoInsuficienteException ex = assertThrows(
                SaldoInsuficienteException.class,
                () -> cuentaService.debitar(CUENTA_ID, new BigDecimal("10000.01"))
        );

        assertEquals("Saldo insuficiente", ex.getMessage());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(0, cuenta.getSaldoContable().compareTo(new BigDecimal("10000.00")));
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debitar exactamente el saldo disponible deja la cuenta en cero")
    void debitarTodoElDisponibleDejaLaCuentaEnCero() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("50000.00"));
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.save(cuenta)).thenReturn(cuenta);

        MovimientoSaldoResponse movimiento = cuentaService.debitar(CUENTA_ID, new BigDecimal("50000"));

        assertEquals(0, movimiento.saldoNuevo().compareTo(BigDecimal.ZERO));
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    @DisplayName("La retencion no se puede debitar: saldo 1000, retencion 300, debitar 800 responde 409")
    void laRetencionNoSePuedeDebitar() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(cuenta, "retencion", new BigDecimal("300.00"));
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        assertThrows(SaldoInsuficienteException.class,
                () -> cuentaService.debitar(CUENTA_ID, new BigDecimal("800.00")));

        assertEquals(0, cuenta.getSaldoContable().compareTo(new BigDecimal("1000.00")));
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debitar lee la cuenta con bloqueo de fila y nunca con findById")
    void debitarUsaLecturaConBloqueo() {
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        when(cuentaRepository.findByIdForUpdate(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.save(cuenta)).thenReturn(cuenta);

        cuentaService.debitar(CUENTA_ID, new BigDecimal("1000.00"));

        verify(cuentaRepository).findByIdForUpdate(CUENTA_ID);
        verify(cuentaRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Debitar un monto nulo lanza IllegalArgumentException sin tocar el repositorio")
    void debitarConMontoNuloLanzaIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> cuentaService.debitar(CUENTA_ID, null));

        assertEquals("Monto inválido", ex.getMessage());
        verifyNoInteractions(cuentaRepository);
    }

    @Test
    @DisplayName("Debitar un monto cero o negativo lanza IllegalArgumentException sin tocar el repositorio")
    void debitarConMontoNoPositivoLanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> cuentaService.debitar(CUENTA_ID, new BigDecimal("0.00")));
        assertThrows(IllegalArgumentException.class,
                () -> cuentaService.debitar(CUENTA_ID, new BigDecimal("-1")));

        verifyNoInteractions(cuentaRepository);
    }

    @Test
    @DisplayName("validarTitularidad no lanza nada si la cuenta es del cliente")
    void validarTitularidadConTitularCorrectoNoLanza() {
        // Arrange
        when(cuentaRepository.existeCuentaDelCliente(CUENTA_ID, CLIENTE_ID)).thenReturn(true);

        // Act & Assert
        assertDoesNotThrow(() -> cuentaService.validarTitularidad(CUENTA_ID, CLIENTE_ID));
        verify(cuentaRepository, never()).findById(any());
    }

    @Test
    @DisplayName("validarTitularidad responde 403 si la cuenta no existe o es de otro cliente (ADR-0005)")
    void validarTitularidadSinCuentaDelClienteRespondeForbidden() {
        // Arrange
        when(cuentaRepository.existeCuentaDelCliente(CUENTA_ID, OTRO_CLIENTE_ID)).thenReturn(false);

        // Act & Assert
        AccesoNoAutorizadoException ex = assertThrows(
                AccesoNoAutorizadoException.class,
                () -> cuentaService.validarTitularidad(CUENTA_ID, OTRO_CLIENTE_ID)
        );

        assertEquals("Acceso no autorizado", ex.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    @DisplayName("obtenerIdPorNumero devuelve el id de la cuenta sin cargar la entidad")
    void obtenerIdPorNumeroDevuelveElId() {
        // Arrange
        when(cuentaRepository.findIdByNumeroCuenta("1234567890")).thenReturn(Optional.of(CUENTA_ID));

        // Act
        UUID id = cuentaService.obtenerIdPorNumero("1234567890");

        // Assert
        assertEquals(CUENTA_ID, id);
        verify(cuentaRepository, never()).findByNumeroCuenta(any());
    }

    @Test
    @DisplayName("obtenerIdPorNumero con un numero inexistente responde 404 'Cuenta no encontrada'")
    void obtenerIdPorNumeroInexistenteRespondeNotFound() {
        // Arrange
        when(cuentaRepository.findIdByNumeroCuenta("0000000000")).thenReturn(Optional.empty());

        // Act & Assert
        CuentaNoEncontradaException ex = assertThrows(
                CuentaNoEncontradaException.class,
                () -> cuentaService.obtenerIdPorNumero("0000000000")
        );

        assertEquals("Cuenta no encontrada", ex.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("bloquearEnOrden bloquea primero la cuenta con el id menor")
    void bloquearEnOrdenBloqueaPrimeroElIdMenor() {
        // Arrange
        UUID idMenor = new UUID(0L, 1L);
        UUID idMayor = new UUID(0L, 2L);
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        when(cuentaRepository.findByIdForUpdate(idMenor)).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.findByIdForUpdate(idMayor)).thenReturn(Optional.of(cuenta));

        // Act
        cuentaService.bloquearEnOrden(idMayor, idMenor);

        // Assert
        InOrder orden = inOrder(cuentaRepository);
        orden.verify(cuentaRepository).findByIdForUpdate(idMenor);
        orden.verify(cuentaRepository).findByIdForUpdate(idMayor);
    }

    @Test
    @DisplayName("buscarIdPorNumero devuelve el id de la cuenta cuando existe")
    void buscarIdPorNumeroDevuelveElId() {
        // Arrange
        when(cuentaRepository.findIdByNumeroCuenta("1234567890")).thenReturn(Optional.of(CUENTA_ID));

        // Act
        Optional<UUID> id = cuentaService.buscarIdPorNumero("1234567890");

        // Assert
        assertEquals(Optional.of(CUENTA_ID), id);
    }

    @Test
    @DisplayName("buscarIdPorNumero devuelve vacio cuando la cuenta no existe, sin lanzar excepcion")
    void buscarIdPorNumeroInexistenteDevuelveVacio() {
        // Arrange
        when(cuentaRepository.findIdByNumeroCuenta("0000000000")).thenReturn(Optional.empty());

        // Act
        Optional<UUID> id = cuentaService.buscarIdPorNumero("0000000000");

        // Assert
        assertTrue(id.isEmpty());
    }

    @Test
    @DisplayName("estaCerrada es verdadero para una cuenta CERRADA")
    void estaCerradaConCuentaCerradaEsVerdadero() {
        // Arrange
        Cuenta cuenta = cuentaConSaldo(BigDecimal.ZERO);
        cuenta.cerrar();
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        // Act & Assert
        assertTrue(cuentaService.estaCerrada(CUENTA_ID));
    }

    @Test
    @DisplayName("estaCerrada es falso para una cuenta BLOQUEADA: puede seguir recibiendo (HU-07a)")
    void estaCerradaConCuentaBloqueadaEsFalso() {
        // Arrange
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        cuenta.bloquear();
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        // Act & Assert
        assertFalse(cuentaService.estaCerrada(CUENTA_ID));
    }

    @Test
    @DisplayName("estaCerrada es falso para una cuenta ACTIVA")
    void estaCerradaConCuentaActivaEsFalso() {
        // Arrange
        Cuenta cuenta = cuentaConSaldo(new BigDecimal("100000.00"));
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));

        // Act & Assert
        assertFalse(cuentaService.estaCerrada(CUENTA_ID));
    }

    @Test
    @DisplayName("estaCerrada con una cuenta inexistente responde 404 'Cuenta no encontrada'")
    void estaCerradaConCuentaInexistenteRespondeNotFound() {
        // Arrange
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.empty());

        // Act & Assert
        CuentaNoEncontradaException ex = assertThrows(
                CuentaNoEncontradaException.class,
                () -> cuentaService.estaCerrada(CUENTA_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
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