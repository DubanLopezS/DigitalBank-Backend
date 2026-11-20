package com.fabricaescuela.digitalbank.transaccion.service;

import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccion;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccion.CuentaTransaccion;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccionAdminResponse;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccionClienteResponse;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.exception.TransaccionNoEncontradaException;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransaccionServiceImpl - detalle de una transaccion y control de acceso")
class TransaccionServiceImplTest {

    private static final UUID TRANSACCION_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ORIGEN = UUID.randomUUID();
    private static final UUID CLIENTE_DESTINO = UUID.randomUUID();
    private static final UUID OTRO_CLIENTE = UUID.randomUUID();
    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final CuentaTransaccion CUENTA_ORIGEN = new CuentaTransaccion("1111111111", CLIENTE_ORIGEN);
    private static final CuentaTransaccion CUENTA_DESTINO = new CuentaTransaccion("2222222222", CLIENTE_DESTINO);

    @Mock
    private ITransaccionRepository transaccionRepository;

    @InjectMocks
    private TransaccionServiceImpl transaccionService;

    @Test
    @DisplayName("Un CLIENTE dueno de la cuenta origen ve la transaccion")
    void clienteDuenoDelOrigenLaVe() {
        // Arrange
        when(transaccionRepository.findDetalleById(TRANSACCION_ID))
                .thenReturn(Optional.of(detalle("RETIRO", CUENTA_ORIGEN, null)));

        // Act
        DetalleTransaccionResponse response = transaccionService.consultarDetalle(
                TRANSACCION_ID, CLIENTE_ORIGEN, "CLIENTE");

        // Assert
        assertThat(response).isInstanceOfSatisfying(DetalleTransaccionClienteResponse.class, cliente -> {
            assertThat(cliente.id()).isEqualTo(TRANSACCION_ID);
            assertThat(cliente.numeroCuentaOrigen()).isEqualTo("1111111111");
            assertThat(cliente.numeroCuentaDestino()).isNull();
        });
    }

    @Test
    @DisplayName("Un CLIENTE dueno de la cuenta destino de una transferencia la ve aunque consulte la fila de salida")
    void clienteDuenoDelDestinoVeLaFilaDeSalida() {
        // Arrange
        when(transaccionRepository.findDetalleById(TRANSACCION_ID))
                .thenReturn(Optional.of(detalle("TRANSFERENCIA", CUENTA_ORIGEN, CUENTA_DESTINO)));

        // Act
        DetalleTransaccionResponse response = transaccionService.consultarDetalle(
                TRANSACCION_ID, CLIENTE_DESTINO, "CLIENTE");

        // Assert
        assertThat(response).isInstanceOfSatisfying(DetalleTransaccionClienteResponse.class, cliente -> {
            assertThat(cliente.numeroCuentaOrigen()).isEqualTo("1111111111");
            assertThat(cliente.numeroCuentaDestino()).isEqualTo("2222222222");
        });
    }

    @Test
    @DisplayName("Un CLIENTE dueno de la cuenta destino de un deposito, que no tiene origen, lo ve")
    void clienteDuenoDelDestinoDeUnDepositoLoVe() {
        // Arrange
        when(transaccionRepository.findDetalleById(TRANSACCION_ID))
                .thenReturn(Optional.of(detalle("DEPOSITO", null, CUENTA_DESTINO)));

        // Act
        DetalleTransaccionResponse response = transaccionService.consultarDetalle(
                TRANSACCION_ID, CLIENTE_DESTINO, "CLIENTE");

        // Assert
        assertThat(response).isInstanceOf(DetalleTransaccionClienteResponse.class);
    }

    @Test
    @DisplayName("Un CLIENTE que no es dueno de ninguna de las dos cuentas recibe 403")
    void clienteConTransaccionAjenaRecibe403() {
        // Arrange
        when(transaccionRepository.findDetalleById(TRANSACCION_ID))
                .thenReturn(Optional.of(detalle("TRANSFERENCIA", CUENTA_ORIGEN, CUENTA_DESTINO)));

        // Act & Assert
        assertThatThrownBy(() -> transaccionService.consultarDetalle(TRANSACCION_ID, OTRO_CLIENTE, "CLIENTE"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessage("Acceso no autorizado");
    }

    @Test
    @DisplayName("Un CLIENTE que consulta una transaccion inexistente recibe 403 y no se le dice que no existe")
    void clienteConTransaccionInexistenteRecibe403() {
        // Arrange
        when(transaccionRepository.findDetalleById(TRANSACCION_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> transaccionService.consultarDetalle(TRANSACCION_ID, CLIENTE_ORIGEN, "CLIENTE"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .extracting(ex -> ((AccesoNoAutorizadoException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Un CLIENTE sin clienteId en el token recibe 403")
    void clienteSinClienteIdRecibe403() {
        // Arrange
        when(transaccionRepository.findDetalleById(TRANSACCION_ID))
                .thenReturn(Optional.of(detalle("RETIRO", CUENTA_ORIGEN, null)));

        // Act & Assert
        assertThatThrownBy(() -> transaccionService.consultarDetalle(TRANSACCION_ID, null, "CLIENTE"))
                .isInstanceOf(AccesoNoAutorizadoException.class);
    }

    @Test
    @DisplayName("Un ADMIN que consulta una transaccion inexistente recibe 404 Transaccion no encontrada")
    void adminConTransaccionInexistenteRecibe404() {
        // Arrange
        when(transaccionRepository.findDetalleById(TRANSACCION_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> transaccionService.consultarDetalle(TRANSACCION_ID, null, "ADMIN"))
                .isInstanceOfSatisfying(TransaccionNoEncontradaException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getErrorCode()).isEqualTo("TRANSACCION_NO_ENCONTRADA");
                    assertThat(ex.getMessage()).isEqualTo("Transacción no encontrada");
                });
    }

    @Test
    @DisplayName("Un ADMIN ve cualquier transaccion con el usuario que la origino")
    void adminVeElUsuario() {
        // Arrange
        when(transaccionRepository.findDetalleById(TRANSACCION_ID))
                .thenReturn(Optional.of(detalle("TRANSFERENCIA", CUENTA_ORIGEN, CUENTA_DESTINO)));

        // Act
        DetalleTransaccionResponse response = transaccionService.consultarDetalle(TRANSACCION_ID, null, "ADMIN");

        // Assert
        assertThat(response).isInstanceOfSatisfying(DetalleTransaccionAdminResponse.class, admin -> {
            assertThat(admin.usuarioId()).isEqualTo(USUARIO_ID);
            assertThat(admin.detalle().id()).isEqualTo(TRANSACCION_ID);
            assertThat(admin.detalle().numeroCuentaOrigen()).isEqualTo("1111111111");
            assertThat(admin.detalle().numeroCuentaDestino()).isEqualTo("2222222222");
        });
    }

    @Test
    @DisplayName("Un CLIENTE recibe la respuesta sin el usuario que origino la transaccion")
    void clienteNoVeElUsuario() {
        // Arrange
        when(transaccionRepository.findDetalleById(TRANSACCION_ID))
                .thenReturn(Optional.of(detalle("TRANSFERENCIA", CUENTA_ORIGEN, CUENTA_DESTINO)));

        // Act
        DetalleTransaccionResponse response = transaccionService.consultarDetalle(
                TRANSACCION_ID, CLIENTE_ORIGEN, "CLIENTE");

        // Assert
        assertThat(response)
                .isInstanceOf(DetalleTransaccionClienteResponse.class)
                .isNotInstanceOf(DetalleTransaccionAdminResponse.class);
        assertThat(DetalleTransaccionClienteResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("usuarioId");
    }

    private DetalleTransaccion detalle(String tipo, CuentaTransaccion origen, CuentaTransaccion destino) {
        return new DetalleTransaccion(TRANSACCION_ID, tipo, new BigDecimal("30000.00"), "COMPLETADA",
                LocalDateTime.of(2026, 10, 5, 10, 15, 30), origen, destino, null, null, null, USUARIO_ID);
    }
}
