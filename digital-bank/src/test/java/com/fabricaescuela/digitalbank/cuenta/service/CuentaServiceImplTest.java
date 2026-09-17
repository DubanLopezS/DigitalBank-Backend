package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
import com.fabricaescuela.digitalbank.cliente.entity.EstadoCliente;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.repository.ClienteRepository;
import com.fabricaescuela.digitalbank.cuenta.dto.AperturaCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoHabilitadoException;
import com.fabricaescuela.digitalbank.cuenta.repository.CuentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {

    private static final String DOCUMENTO = "1020304050";

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private GeneradorNumeroCuenta generadorNumeroCuenta;

    @InjectMocks
    private CuentaServiceImpl cuentaService;

    @Test
    @DisplayName("AC-2: cliente INACTIVO no puede abrir cuenta, responde 403 'Cliente no habilitado'")
    void clienteInactivoNoPuedeAbrirCuenta() {
        Cliente cliente = mock(Cliente.class);
        when(cliente.getEstado()).thenReturn(EstadoCliente.INACTIVO);
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.of(cliente));

        ClienteNoHabilitadoException ex = assertThrows(
                ClienteNoHabilitadoException.class,
                () -> cuentaService.abrirCuenta(request(BigDecimal.valueOf(50000)))
        );

        assertEquals("Cliente no habilitado", ex.getMessage());
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cliente inexistente responde 404 'Cliente no encontrado'")
    void clienteInexistenteRespondeNotFound() {
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.empty());

        ClienteNoEncontradoException ex = assertThrows(
                ClienteNoEncontradoException.class,
                () -> cuentaService.abrirCuenta(request(null))
        );

        assertEquals("Cliente no encontrado", ex.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC-4: sin monto de apertura la cuenta inicia en 0")
    void sinMontoLaCuentaIniciaEnCero() {
        prepararClienteActivo();

        CuentaResponse response = cuentaService.abrirCuenta(request(null));

        assertEquals(0, response.saldoContable().compareTo(BigDecimal.ZERO));
        assertEquals(0, response.saldoDisponible().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("AC-3 y AC-5: la cuenta se crea ACTIVA, con numero de 10 digitos y el saldo indicado")
    void cuentaSeCreaActivaConNumeroDeDiezDigitos() {
        prepararClienteActivo();

        CuentaResponse response = cuentaService.abrirCuenta(request(BigDecimal.valueOf(150000)));

        assertEquals("ACTIVA", response.estado());
        assertEquals(10, response.numeroCuenta().length());
        assertEquals(TipoCuenta.AHORROS, response.tipoCuenta());
        assertEquals(0, response.saldoContable().compareTo(BigDecimal.valueOf(150000)));
        assertEquals(0, response.retencion().compareTo(BigDecimal.ZERO));
        assertNotNull(response.fechaApertura());
    }

    private void prepararClienteActivo() {
        Cliente cliente = mock(Cliente.class);
        when(cliente.getEstado()).thenReturn(EstadoCliente.ACTIVO);
        when(cliente.getId()).thenReturn(UUID.randomUUID());
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(Optional.of(cliente));
        when(generadorNumeroCuenta.generar()).thenReturn("1234567890");
        when(cuentaRepository.save(any(Cuenta.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AperturaCuentaRequest request(BigDecimal monto) {
        return new AperturaCuentaRequest(TipoDocumento.CC, DOCUMENTO, TipoCuenta.AHORROS, monto);
    }
}