package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cuenta.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.OrigenDeposito;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.cuenta.exception.MontoInvalidoException;
import com.fabricaescuela.digitalbank.cuenta.repository.CuentaRepository;
import com.fabricaescuela.digitalbank.cuenta.repository.TransaccionRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositoServiceImplTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ID = UUID.randomUUID();

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private TransaccionRepository transaccionRepository;

    @InjectMocks
    private DepositoServiceImpl depositoService;

    @BeforeAll
    static void crearValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void cerrarValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("Con un monto valido se acredita el saldo y se registra la transaccion")
    void registrarDeposito_montoValido_debeActualizarSaldo() {
        Cuenta cuenta = new Cuenta(CLIENTE_ID, "1234567890", TipoCuenta.AHORROS, BigDecimal.valueOf(100000));
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.save(any(Cuenta.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transaccionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DepositoRequest request = new DepositoRequest(BigDecimal.valueOf(50000), OrigenDeposito.EFECTIVO);
        TransaccionResponse response = depositoService.registrarDeposito(CUENTA_ID, request);

        assertEquals(0, cuenta.getSaldoContable().compareTo(BigDecimal.valueOf(150000)));
        assertEquals(0, response.saldoAnterior().compareTo(BigDecimal.valueOf(100000)));
        assertEquals(0, response.saldoNuevo().compareTo(BigDecimal.valueOf(150000)));
        assertEquals(0, response.monto().compareTo(BigDecimal.valueOf(50000)));
        assertEquals("EFECTIVO", response.origen());
        verify(cuentaRepository).save(cuenta);
        verify(transaccionRepository).save(any());
    }

    @Test
    @DisplayName("Cuenta inexistente responde con CuentaNoEncontradaException")
    void registrarDeposito_cuentaInexistente_debeLanzarExcepcion() {
        when(cuentaRepository.findById(CUENTA_ID)).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class,
                () -> depositoService.registrarDeposito(CUENTA_ID, new DepositoRequest(BigDecimal.TEN, OrigenDeposito.EFECTIVO)));

        verify(transaccionRepository, never()).save(any());
    }

    @Test
    @DisplayName("El service rechaza un monto cero aunque no pase por la validacion del DTO")
    void registrarDeposito_montoCeroEnService_debeLanzarExcepcion() {
        MontoInvalidoException ex = assertThrows(
                MontoInvalidoException.class,
                () -> depositoService.registrarDeposito(CUENTA_ID, new DepositoRequest(BigDecimal.ZERO, OrigenDeposito.EFECTIVO))
        );

        assertEquals("Monto inválido", ex.getMessage());
        verifyNoInteractions(cuentaRepository, transaccionRepository);
    }

    @Test
    @DisplayName("El service rechaza un monto negativo aunque no pase por la validacion del DTO")
    void registrarDeposito_montoNegativoEnService_debeLanzarExcepcion() {
        MontoInvalidoException ex = assertThrows(
                MontoInvalidoException.class,
                () -> depositoService.registrarDeposito(CUENTA_ID, new DepositoRequest(BigDecimal.valueOf(-1), OrigenDeposito.EFECTIVO))
        );

        assertEquals("Monto inválido", ex.getMessage());
        verifyNoInteractions(cuentaRepository, transaccionRepository);
    }

    @Test
    @DisplayName("Monto cero es rechazado por la validacion del DTO (DepositoRequest), no por el service")
    void registrarDeposito_montoCero_debeRechazar() {
        Set<ConstraintViolation<DepositoRequest>> violaciones =
                validator.validate(new DepositoRequest(BigDecimal.ZERO, OrigenDeposito.EFECTIVO));

        assertFalse(violaciones.isEmpty());
        assertEquals("Monto inválido", violaciones.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Monto negativo es rechazado por la validacion del DTO (DepositoRequest), no por el service")
    void registrarDeposito_montoNegativo_debeRechazar() {
        Set<ConstraintViolation<DepositoRequest>> violaciones =
                validator.validate(new DepositoRequest(BigDecimal.valueOf(-1), OrigenDeposito.EFECTIVO));

        assertFalse(violaciones.isEmpty());
        assertEquals("Monto inválido", violaciones.iterator().next().getMessage());
    }
}
