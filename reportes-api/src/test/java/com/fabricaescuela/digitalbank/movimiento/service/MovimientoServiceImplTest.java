package com.fabricaescuela.digitalbank.movimiento.service;

import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.movimiento.dto.HistorialMovimientosResponse;
import com.fabricaescuela.digitalbank.movimiento.dto.MovimientoResponse;
import com.fabricaescuela.digitalbank.movimiento.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.movimiento.exception.PaginacionInvalidaException;
import com.fabricaescuela.digitalbank.movimiento.exception.RangoFechasInvalidoException;
import com.fabricaescuela.digitalbank.movimiento.exception.TipoMovimientoInvalidoException;
import com.fabricaescuela.digitalbank.movimiento.interfaces.repositories.IMovimientoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MovimientoServiceImpl - historial de movimientos y control de acceso")
class MovimientoServiceImplTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final UUID CLIENTE_TITULAR = UUID.randomUUID();
    private static final UUID OTRO_CLIENTE = UUID.randomUUID();
    private static final LocalDate INICIO = LocalDate.of(2026, 10, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 10, 15);

    @Mock
    private IMovimientoRepository movimientoRepository;

    @InjectMocks
    private MovimientoServiceImpl movimientoService;

    @Test
    @DisplayName("Un ADMIN consulta una cuenta ajena y recibe la pagina con el total")
    void adminConsultaCualquierCuenta() {
        // Arrange
        List<MovimientoResponse> movimientos = List.of(deposito());
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.of(OTRO_CLIENTE));
        when(movimientoRepository.findMovimientos(CUENTA_ID, INICIO, FIN, "DEPOSITO", 2, 10)).thenReturn(movimientos);
        when(movimientoRepository.contarMovimientos(CUENTA_ID, INICIO, FIN, "DEPOSITO")).thenReturn(21L);

        // Act
        HistorialMovimientosResponse response = movimientoService.consultarMovimientos(
                CUENTA_ID, INICIO, FIN, "DEPOSITO", 2, 10, null, "ADMIN");

        // Assert
        assertThat(response.contenido()).isEqualTo(movimientos);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(21L);
    }

    @Test
    @DisplayName("Un CAJERO consulta una cuenta ajena sin verificar titularidad")
    void cajeroConsultaCualquierCuenta() {
        // Arrange
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.of(OTRO_CLIENTE));
        when(movimientoRepository.findMovimientos(CUENTA_ID, null, null, null, 0, 20)).thenReturn(List.of());

        // Act
        HistorialMovimientosResponse response = movimientoService.consultarMovimientos(
                CUENTA_ID, null, null, null, 0, 20, null, "CAJERO");

        // Assert
        assertThat(response.contenido()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    @DisplayName("El cliente titular consulta los movimientos de su propia cuenta")
    void clienteTitularConsultaSuCuenta() {
        // Arrange
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.of(CLIENTE_TITULAR));
        when(movimientoRepository.findMovimientos(CUENTA_ID, null, null, null, 0, 20)).thenReturn(List.of(deposito()));
        when(movimientoRepository.contarMovimientos(CUENTA_ID, null, null, null)).thenReturn(1L);

        // Act
        HistorialMovimientosResponse response = movimientoService.consultarMovimientos(
                CUENTA_ID, null, null, null, 0, 20, CLIENTE_TITULAR, "CLIENTE");

        // Assert
        assertThat(response.contenido()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Un cliente que no es titular recibe 403 y no se consultan movimientos")
    void clienteNoTitularRecibe403() {
        // Arrange
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.of(OTRO_CLIENTE));

        // Act & Assert
        assertThatThrownBy(() -> movimientoService.consultarMovimientos(
                CUENTA_ID, null, null, null, 0, 20, CLIENTE_TITULAR, "CLIENTE"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessage("Acceso no autorizado");
        verify(movimientoRepository, never()).findMovimientos(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Un CLIENTE sin clienteId en el token recibe 403")
    void clienteSinClienteIdRecibe403() {
        // Arrange
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.of(CLIENTE_TITULAR));

        // Act & Assert
        assertThatThrownBy(() -> movimientoService.consultarMovimientos(
                CUENTA_ID, null, null, null, 0, 20, null, "CLIENTE"))
                .isInstanceOf(AccesoNoAutorizadoException.class);
    }

    @Test
    @DisplayName("Para el personal autorizado una cuenta inexistente responde 404")
    void cuentaInexistenteResponde404AlPersonal() {
        // Arrange
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> movimientoService.consultarMovimientos(
                CUENTA_ID, null, null, null, 0, 20, null, "CAJERO"))
                .isInstanceOf(CuentaNoEncontradaException.class)
                .extracting(ex -> ((CuentaNoEncontradaException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Para un cliente una cuenta inexistente responde 403 y no revela si existe")
    void cuentaInexistenteResponde403AlCliente() {
        // Arrange
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> movimientoService.consultarMovimientos(
                CUENTA_ID, null, null, null, 0, 20, CLIENTE_TITULAR, "CLIENTE"))
                .isInstanceOf(AccesoNoAutorizadoException.class);
    }

    @Test
    @DisplayName("Si fecha_inicio es posterior a fecha_fin responde 400 sin tocar la base")
    void rangoDeFechasInvalido() {
        // Act & Assert
        assertThatThrownBy(() -> movimientoService.consultarMovimientos(
                CUENTA_ID, FIN, INICIO, null, 0, 20, null, "ADMIN"))
                .isInstanceOf(RangoFechasInvalidoException.class)
                .hasMessage("Rango de fechas inválido")
                .extracting(ex -> ((RangoFechasInvalidoException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("La misma fecha de inicio y fin es un rango valido")
    void mismaFechaEsRangoValido() {
        // Arrange
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.of(OTRO_CLIENTE));
        when(movimientoRepository.findMovimientos(CUENTA_ID, INICIO, INICIO, null, 0, 20)).thenReturn(List.of());

        // Act
        HistorialMovimientosResponse response = movimientoService.consultarMovimientos(
                CUENTA_ID, INICIO, INICIO, null, 0, 20, null, "ADMIN");

        // Assert
        assertThat(response.contenido()).isEmpty();
    }

    @Test
    @DisplayName("REVERSO es un tipo valido aunque todavia no haya filas de ese tipo")
    void reversoEsUnTipoValido() {
        // Arrange
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.of(OTRO_CLIENTE));
        when(movimientoRepository.findMovimientos(CUENTA_ID, null, null, "REVERSO", 0, 20)).thenReturn(List.of());

        // Act
        HistorialMovimientosResponse response = movimientoService.consultarMovimientos(
                CUENTA_ID, null, null, "REVERSO", 0, 20, null, "ADMIN");

        // Assert
        assertThat(response.contenido()).isEmpty();
        verify(movimientoRepository).contarMovimientos(CUENTA_ID, null, null, "REVERSO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAGO", "deposito", ""})
    @DisplayName("Un tipo que no es DEPOSITO, RETIRO, TRANSFERENCIA ni REVERSO responde 400")
    void tipoInvalido(String tipo) {
        // Act & Assert
        assertThatThrownBy(() -> movimientoService.consultarMovimientos(
                CUENTA_ID, null, null, tipo, 0, 20, null, "ADMIN"))
                .isInstanceOf(TipoMovimientoInvalidoException.class)
                .hasMessage("Tipo de movimiento inválido");
        verifyNoInteractions(movimientoRepository);
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 101"})
    @DisplayName("Una pagina negativa o un size fuera de 1..100 responde 400")
    void paginacionInvalida(int page, int size) {
        // Act & Assert
        assertThatThrownBy(() -> movimientoService.consultarMovimientos(
                CUENTA_ID, null, null, null, page, size, null, "ADMIN"))
                .isInstanceOf(PaginacionInvalidaException.class);
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    @DisplayName("Un size de 100 es el maximo permitido")
    void sizeCienEsValido() {
        // Arrange
        when(movimientoRepository.findClienteIdByCuentaId(CUENTA_ID)).thenReturn(Optional.of(OTRO_CLIENTE));
        when(movimientoRepository.findMovimientos(CUENTA_ID, null, null, null, 0, 100)).thenReturn(List.of());

        // Act
        HistorialMovimientosResponse response = movimientoService.consultarMovimientos(
                CUENTA_ID, null, null, null, 0, 100, null, "ADMIN");

        // Assert
        assertThat(response.size()).isEqualTo(100);
    }

    private MovimientoResponse deposito() {
        return new MovimientoResponse(UUID.randomUUID(), "DEPOSITO", new BigDecimal("50000.00"),
                LocalDateTime.of(2026, 10, 5, 10, 0), new BigDecimal("0.00"), new BigDecimal("50000.00"),
                "COMPLETADA", "EFECTIVO", null, null, null, null);
    }
}
