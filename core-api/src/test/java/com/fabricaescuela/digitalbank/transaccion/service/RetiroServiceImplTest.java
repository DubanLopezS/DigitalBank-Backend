package com.fabricaescuela.digitalbank.transaccion.service;

import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.dto.MovimientoSaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoDisponibleException;
import com.fabricaescuela.digitalbank.cuenta.exception.SaldoInsuficienteException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;
import com.fabricaescuela.digitalbank.transaccion.dto.RetiroRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.entity.TipoTransaccion;
import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetiroServiceImpl - registro de retiros")
class RetiroServiceImplTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ID = UUID.randomUUID();

    @Mock
    private ICuentaService cuentaService;

    @Mock
    private ITransaccionRepository transaccionRepository;

    @Captor
    private ArgumentCaptor<Transaccion> transaccionCaptor;

    @InjectMocks
    private RetiroServiceImpl retiroService;

    @Test
    @DisplayName("El retiro valida la titularidad antes de debitar la cuenta")
    void elRetiroValidaTitularidadAntesDeDebitar() {
        // Arrange
        prepararDebito(new BigDecimal("30000.00"), new BigDecimal("100000.00"), new BigDecimal("70000.00"));

        // Act
        retiroService.registrarRetiro(CUENTA_ID, new RetiroRequest(new BigDecimal("30000.00")), CLIENTE_ID);

        // Assert
        InOrder orden = inOrder(cuentaService);
        orden.verify(cuentaService).validarTitularidad(CUENTA_ID, CLIENTE_ID);
        orden.verify(cuentaService).debitar(CUENTA_ID, new BigDecimal("30000.00"));
    }

    @Test
    @DisplayName("La transaccion registra el saldo antes y despues del retiro")
    void laTransaccionRegistraElSaldoAnteriorYElNuevo() {
        // Arrange
        prepararDebito(new BigDecimal("30000.00"), new BigDecimal("100000.00"), new BigDecimal("70000.00"));

        // Act
        retiroService.registrarRetiro(CUENTA_ID, new RetiroRequest(new BigDecimal("30000.00")), CLIENTE_ID);

        // Assert
        verify(transaccionRepository).save(transaccionCaptor.capture());
        Transaccion transaccion = transaccionCaptor.getValue();
        assertThat(transaccion.getCuentaId()).isEqualTo(CUENTA_ID);
        assertThat(transaccion.getTipo()).isEqualTo(TipoTransaccion.RETIRO);
        assertThat(transaccion.getMonto()).isEqualByComparingTo(new BigDecimal("30000.00"));
        assertThat(transaccion.getSaldoAnterior()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(transaccion.getSaldoNuevo()).isEqualByComparingTo(new BigDecimal("70000.00"));
    }

    @Test
    @DisplayName("La respuesta expone la transaccion COMPLETADA de tipo RETIRO")
    void laRespuestaExponeLaTransaccionCompletada() {
        // Arrange
        prepararDebito(new BigDecimal("100000.00"), new BigDecimal("100000.00"), BigDecimal.ZERO);

        // Act
        TransaccionResponse response = retiroService.registrarRetiro(
                CUENTA_ID, new RetiroRequest(new BigDecimal("100000.00")), CLIENTE_ID);

        // Assert
        assertThat(response.cuentaId()).isEqualTo(CUENTA_ID);
        assertThat(response.tipo()).isEqualTo("RETIRO");
        assertThat(response.estado()).isEqualTo("COMPLETADA");
        assertThat(response.origen()).isNull();
        assertThat(response.monto()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(response.saldoAnterior()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(response.saldoNuevo()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.fechaHora()).isNotNull();
    }

    @Test
    @DisplayName("Si la cuenta no es del cliente la excepcion se propaga sin debitar ni registrar")
    void cuentaAjenaPropagaLaExcepcion() {
        // Arrange
        AccesoNoAutorizadoException excepcion = new AccesoNoAutorizadoException();
        doThrow(excepcion).when(cuentaService).validarTitularidad(CUENTA_ID, CLIENTE_ID);

        // Act & Assert
        assertThatThrownBy(() -> retiroService.registrarRetiro(
                CUENTA_ID, new RetiroRequest(new BigDecimal("30000.00")), CLIENTE_ID))
                .isSameAs(excepcion);

        verify(cuentaService, never()).debitar(any(UUID.class), any(BigDecimal.class));
        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("Si el saldo no alcanza la excepcion se propaga y no se registra transaccion")
    void saldoInsuficientePropagaLaExcepcion() {
        // Arrange
        SaldoInsuficienteException excepcion = new SaldoInsuficienteException();
        when(cuentaService.debitar(CUENTA_ID, new BigDecimal("30000.00"))).thenThrow(excepcion);

        // Act & Assert
        assertThatThrownBy(() -> retiroService.registrarRetiro(
                CUENTA_ID, new RetiroRequest(new BigDecimal("30000.00")), CLIENTE_ID))
                .isSameAs(excepcion);

        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("Si la cuenta no esta disponible la excepcion se propaga sin registrar")
    void cuentaNoDisponiblePropagaLaExcepcion() {
        // Arrange
        CuentaNoDisponibleException excepcion = new CuentaNoDisponibleException("Cuenta no disponible");
        when(cuentaService.debitar(CUENTA_ID, new BigDecimal("30000.00"))).thenThrow(excepcion);

        // Act & Assert
        assertThatThrownBy(() -> retiroService.registrarRetiro(
                CUENTA_ID, new RetiroRequest(new BigDecimal("30000.00")), CLIENTE_ID))
                .isSameAs(excepcion);

        verifyNoInteractions(transaccionRepository);
    }

    private void prepararDebito(BigDecimal monto, BigDecimal saldoAnterior, BigDecimal saldoNuevo) {
        when(cuentaService.debitar(CUENTA_ID, monto))
                .thenReturn(new MovimientoSaldoResponse(CUENTA_ID, saldoAnterior, saldoNuevo));
        when(transaccionRepository.save(any(Transaccion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }
}
