package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteConsulta;
import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResumenResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoCuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cuenta.exception.FiltroInvalidoException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListadoCuentasServiceImpl - listado de cuentas por cliente")
class ListadoCuentasServiceImplTest {

    private static final UUID CLIENTE_ID = UUID.randomUUID();

    @Mock
    private ICuentaRepository cuentaRepository;

    @Mock
    private IClienteConsulta clienteConsulta;

    @InjectMocks
    private ListadoCuentasServiceImpl service;

    @Test
    @DisplayName("Un CLIENTE lista sus propias cuentas con el saldo disponible")
    void clienteListaSusCuentas() {
        // Arrange
        Cuenta cuenta = new Cuenta(CLIENTE_ID, "1234567890", TipoCuenta.AHORROS, new BigDecimal("100.00"));
        when(cuentaRepository.findByClienteIdOrderByFechaAperturaAsc(CLIENTE_ID)).thenReturn(List.of(cuenta));

        // Act
        List<CuentaResumenResponse> resultado = service.listar(CLIENTE_ID, null, CLIENTE_ID);

        // Assert
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).numeroCuenta()).isEqualTo("1234567890");
        assertThat(resultado.get(0).saldoDisponible()).isEqualByComparingTo("100.00");
        assertThat(resultado.get(0).estado()).isEqualTo(EstadoCuenta.ACTIVA);
        verifyNoInteractions(clienteConsulta);
    }

    @Test
    @DisplayName("Un CLIENTE que pide otro clienteId recibe 403 sin consultar nada")
    void clienteConOtroIdRecibe403() {
        // Arrange
        UUID otroCliente = UUID.randomUUID();

        // Act + Assert
        assertThatThrownBy(() -> service.listar(otroCliente, null, CLIENTE_ID))
                .isInstanceOf(AccesoNoAutorizadoException.class);
        verifyNoInteractions(cuentaRepository, clienteConsulta);
    }

    @Test
    @DisplayName("ADMIN o CAJERO reciben 404 si el cliente no existe")
    void personalInternoRecibe404SiNoExiste() {
        // Arrange
        when(clienteConsulta.existePorId(CLIENTE_ID)).thenReturn(false);

        // Act + Assert
        assertThatThrownBy(() -> service.listar(CLIENTE_ID, null, null))
                .isInstanceOf(ClienteNoEncontradoException.class);
        verifyNoInteractions(cuentaRepository);
    }

    @Test
    @DisplayName("ADMIN o CAJERO listan las cuentas de un cliente existente")
    void personalInternoListaCuentas() {
        // Arrange
        Cuenta cuenta = new Cuenta(CLIENTE_ID, "1234567890", TipoCuenta.CORRIENTE);
        when(clienteConsulta.existePorId(CLIENTE_ID)).thenReturn(true);
        when(cuentaRepository.findByClienteIdOrderByFechaAperturaAsc(CLIENTE_ID)).thenReturn(List.of(cuenta));

        // Act
        List<CuentaResumenResponse> resultado = service.listar(CLIENTE_ID, null, null);

        // Assert
        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("Un cliente sin cuentas recibe una lista vacia")
    void clienteSinCuentasRecibeListaVacia() {
        // Arrange
        when(cuentaRepository.findByClienteIdOrderByFechaAperturaAsc(CLIENTE_ID)).thenReturn(List.of());

        // Act
        List<CuentaResumenResponse> resultado = service.listar(CLIENTE_ID, null, CLIENTE_ID);

        // Assert
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("El filtro de estado acepta minusculas")
    void filtroAceptaMinusculas() {
        // Arrange
        when(cuentaRepository.findByClienteIdAndEstadoOrderByFechaAperturaAsc(CLIENTE_ID, EstadoCuenta.BLOQUEADA))
                .thenReturn(List.of());

        // Act
        service.listar(CLIENTE_ID, "bloqueada", CLIENTE_ID);

        // Assert
        verify(cuentaRepository)
                .findByClienteIdAndEstadoOrderByFechaAperturaAsc(CLIENTE_ID, EstadoCuenta.BLOQUEADA);
    }

    @Test
    @DisplayName("Un filtro desconocido responde FILTRO_INVALIDO")
    void filtroDesconocidoEsInvalido() {
        // Act + Assert
        assertThatThrownBy(() -> service.listar(CLIENTE_ID, "CONGELADA", CLIENTE_ID))
                .isInstanceOf(FiltroInvalidoException.class);
        verifyNoInteractions(cuentaRepository);
    }

    @Test
    @DisplayName("Un filtro vacio responde FILTRO_INVALIDO")
    void filtroVacioEsInvalido() {
        // Act + Assert
        assertThatThrownBy(() -> service.listar(CLIENTE_ID, "", CLIENTE_ID))
                .isInstanceOf(FiltroInvalidoException.class);
    }
}