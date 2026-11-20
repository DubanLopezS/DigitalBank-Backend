package com.fabricaescuela.digitalbank.transaccion.repository;

import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccion;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccion.CuentaTransaccion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TransaccionJdbcRepository.class)
@Sql("/sql/esquema-movimientos.sql")
@DisplayName("TransaccionJdbcRepository - detalle de una transaccion sobre H2 en modo PostgreSQL")
class TransaccionJdbcRepositoryTest {

    private static final UUID CLIENTE_A = UUID.randomUUID();
    private static final UUID CLIENTE_B = UUID.randomUUID();
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 5, 10, 15, 30, 123_000_000);

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TransaccionJdbcRepository repository;

    @Test
    @DisplayName("Un deposito tiene como destino la cuenta de la fila y no tiene origen")
    void depositoTieneSoloDestino() {
        // Arrange
        UUID cuentaId = crearCuenta("1111111111", CLIENTE_A);
        UUID id = insertar(new Fila(cuentaId, "DEPOSITO").conOrigen("EFECTIVO"));

        // Act
        DetalleTransaccion detalle = repository.findDetalleById(id).orElseThrow();

        // Assert
        assertThat(detalle.id()).isEqualTo(id);
        assertThat(detalle.tipo()).isEqualTo("DEPOSITO");
        assertThat(detalle.monto()).isEqualByComparingTo("50000.00");
        assertThat(detalle.estado()).isEqualTo("COMPLETADA");
        assertThat(detalle.fechaHora()).isEqualTo(FECHA);
        assertThat(detalle.origen()).isEqualTo("EFECTIVO");
        assertThat(detalle.cuentaOrigen()).isNull();
        assertThat(detalle.cuentaDestino()).isEqualTo(new CuentaTransaccion("1111111111", CLIENTE_A));
        assertThat(detalle.transaccionRelacionadaId()).isNull();
    }

    @Test
    @DisplayName("Un retiro tiene como origen la cuenta de la fila y no tiene destino")
    void retiroTieneSoloOrigen() {
        // Arrange
        UUID cuentaId = crearCuenta("1111111111", CLIENTE_A);
        UUID id = insertar(new Fila(cuentaId, "RETIRO"));

        // Act
        DetalleTransaccion detalle = repository.findDetalleById(id).orElseThrow();

        // Assert
        assertThat(detalle.tipo()).isEqualTo("RETIRO");
        assertThat(detalle.origen()).isNull();
        assertThat(detalle.cuentaOrigen()).isEqualTo(new CuentaTransaccion("1111111111", CLIENTE_A));
        assertThat(detalle.cuentaDestino()).isNull();
    }

    @Test
    @DisplayName("En la fila SALIENTE de una transferencia la cuenta de la fila es el origen y la otra el destino")
    void transferenciaSalienteTieneOrigenYDestino() {
        // Arrange
        UUID origen = crearCuenta("1111111111", CLIENTE_A);
        UUID destino = crearCuenta("2222222222", CLIENTE_B);
        UUID transferenciaId = UUID.randomUUID();
        UUID salida = insertar(new Fila(origen, "TRANSFERENCIA").enTransferencia(transferenciaId, "SALIENTE"));
        insertar(new Fila(destino, "TRANSFERENCIA").enTransferencia(transferenciaId, "ENTRANTE"));

        // Act
        DetalleTransaccion detalle = repository.findDetalleById(salida).orElseThrow();

        // Assert
        assertThat(detalle.id()).isEqualTo(salida);
        assertThat(detalle.cuentaOrigen()).isEqualTo(new CuentaTransaccion("1111111111", CLIENTE_A));
        assertThat(detalle.cuentaDestino()).isEqualTo(new CuentaTransaccion("2222222222", CLIENTE_B));
        assertThat(detalle.descripcion()).isEqualTo("Pago arriendo");
    }

    @Test
    @DisplayName("En la fila ENTRANTE de una transferencia la otra cuenta es el origen y la de la fila el destino")
    void transferenciaEntranteTieneOrigenYDestino() {
        // Arrange
        UUID origen = crearCuenta("1111111111", CLIENTE_A);
        UUID destino = crearCuenta("2222222222", CLIENTE_B);
        UUID transferenciaId = UUID.randomUUID();
        insertar(new Fila(origen, "TRANSFERENCIA").enTransferencia(transferenciaId, "SALIENTE"));
        UUID entrada = insertar(new Fila(destino, "TRANSFERENCIA").enTransferencia(transferenciaId, "ENTRANTE"));

        // Act
        DetalleTransaccion detalle = repository.findDetalleById(entrada).orElseThrow();

        // Assert
        assertThat(detalle.id()).isEqualTo(entrada);
        assertThat(detalle.cuentaOrigen()).isEqualTo(new CuentaTransaccion("1111111111", CLIENTE_A));
        assertThat(detalle.cuentaDestino()).isEqualTo(new CuentaTransaccion("2222222222", CLIENTE_B));
    }

    @Test
    @DisplayName("Devuelve el usuario que origino la transaccion")
    void transaccionConUsuario() {
        // Arrange
        UUID usuarioId = UUID.randomUUID();
        UUID cuentaId = crearCuenta("1111111111", CLIENTE_A);
        UUID id = insertar(new Fila(cuentaId, "RETIRO").conUsuario(usuarioId));

        // Act + Assert
        assertThat(repository.findDetalleById(id).orElseThrow().usuarioId()).isEqualTo(usuarioId);
    }

    @Test
    @DisplayName("Una transaccion anterior a la V6, sin usuario, devuelve el usuario nulo")
    void transaccionSinUsuario() {
        // Arrange
        UUID cuentaId = crearCuenta("1111111111", CLIENTE_A);
        UUID id = insertar(new Fila(cuentaId, "DEPOSITO").conOrigen("CHEQUE"));

        // Act + Assert
        assertThat(repository.findDetalleById(id).orElseThrow().usuarioId()).isNull();
    }

    @Test
    @DisplayName("Una transferencia no sale duplicada aunque un reverso use el mismo transferencia_id")
    void transferenciaNoSaleDuplicadaConUnReversoDelMismoTransferenciaId() {
        // Arrange
        UUID origen = crearCuenta("1111111111", CLIENTE_A);
        UUID destino = crearCuenta("2222222222", CLIENTE_B);
        UUID transferenciaId = UUID.randomUUID();
        UUID salida = insertar(new Fila(origen, "TRANSFERENCIA").enTransferencia(transferenciaId, "SALIENTE"));
        insertar(new Fila(destino, "TRANSFERENCIA").enTransferencia(transferenciaId, "ENTRANTE"));
        UUID reverso = insertar(new Fila(origen, "REVERSO").enTransferencia(transferenciaId, "ENTRANTE")
                .conUsuario(UUID.randomUUID()).reversoDe(salida));

        // Act
        DetalleTransaccion detalle = repository.findDetalleById(salida).orElseThrow();

        // Assert
        assertThat(detalle.id()).isEqualTo(salida);
        assertThat(detalle.cuentaDestino()).isEqualTo(new CuentaTransaccion("2222222222", CLIENTE_B));
        assertThat(repository.findDetalleById(reverso)).isPresent();
    }

    @Test
    @DisplayName("Un reverso ENTRANTE tiene como destino la cuenta de la fila y apunta a su original")
    void reversoEntranteTieneDestinoYOriginal() {
        // Arrange
        UUID cuentaId = crearCuenta("1111111111", CLIENTE_A);
        UUID original = insertar(new Fila(cuentaId, "RETIRO"));
        UUID reverso = insertar(new Fila(cuentaId, "REVERSO").enTransferencia(null, "ENTRANTE")
                .conUsuario(UUID.randomUUID()).reversoDe(original));

        // Act
        DetalleTransaccion detalle = repository.findDetalleById(reverso).orElseThrow();

        // Assert
        assertThat(detalle.tipo()).isEqualTo("REVERSO");
        assertThat(detalle.cuentaOrigen()).isNull();
        assertThat(detalle.cuentaDestino()).isEqualTo(new CuentaTransaccion("1111111111", CLIENTE_A));
        assertThat(detalle.transaccionRelacionadaId()).isEqualTo(original);
    }

    @Test
    @DisplayName("Un reverso SALIENTE tiene como origen la cuenta de la fila")
    void reversoSalienteTieneOrigen() {
        // Arrange
        UUID cuentaId = crearCuenta("1111111111", CLIENTE_A);
        UUID original = insertar(new Fila(cuentaId, "DEPOSITO").conOrigen("EFECTIVO"));
        UUID reverso = insertar(new Fila(cuentaId, "REVERSO").enTransferencia(null, "SALIENTE")
                .conUsuario(UUID.randomUUID()).reversoDe(original));

        // Act
        DetalleTransaccion detalle = repository.findDetalleById(reverso).orElseThrow();

        // Assert
        assertThat(detalle.cuentaOrigen()).isEqualTo(new CuentaTransaccion("1111111111", CLIENTE_A));
        assertThat(detalle.cuentaDestino()).isNull();
    }

    @Test
    @DisplayName("Una transaccion que no existe devuelve vacio")
    void transaccionInexistenteDevuelveVacio() {
        assertThat(repository.findDetalleById(UUID.randomUUID())).isEmpty();
    }

    private UUID crearCuenta(String numeroCuenta, UUID clienteId) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO cuenta (id, cliente_id, numero_cuenta, tipo_cuenta, saldo_contable, estado)
                        VALUES (:id, :clienteId, :numero, 'AHORROS', 100000.00, 'ACTIVA')
                        """)
                .param("id", id)
                .param("clienteId", clienteId)
                .param("numero", numeroCuenta)
                .update();
        return id;
    }

    private UUID insertar(Fila fila) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO transaccion (id, cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo, origen,
                                                 fecha_hora, transferencia_id, sentido, descripcion,
                                                 usuario_id, motivo, transaccion_relacionada_id)
                        VALUES (:id, :cuentaId, :tipo, 50000.00, 100000.00, 50000.00, :origen,
                                :fechaHora, :transferenciaId, :sentido, :descripcion,
                                :usuarioId, :motivo, :relacionadaId)
                        """)
                .param("id", id)
                .param("cuentaId", fila.cuentaId)
                .param("tipo", fila.tipo)
                .param("origen", fila.origen)
                .param("fechaHora", FECHA)
                .param("transferenciaId", fila.transferenciaId)
                .param("sentido", fila.sentido)
                .param("descripcion", fila.descripcion)
                .param("usuarioId", fila.usuarioId)
                .param("motivo", fila.motivo)
                .param("relacionadaId", fila.relacionadaId)
                .update();
        return id;
    }

    private static final class Fila {
        private final UUID cuentaId;
        private final String tipo;
        private String origen;
        private UUID transferenciaId;
        private String sentido;
        private String descripcion;
        private UUID usuarioId;
        private String motivo;
        private UUID relacionadaId;

        private Fila(UUID cuentaId, String tipo) {
            this.cuentaId = cuentaId;
            this.tipo = tipo;
        }

        private Fila conOrigen(String origen) {
            this.origen = origen;
            return this;
        }

        private Fila enTransferencia(UUID transferenciaId, String sentido) {
            this.transferenciaId = transferenciaId;
            this.sentido = sentido;
            this.descripcion = "Pago arriendo";
            return this;
        }

        private Fila conUsuario(UUID usuarioId) {
            this.usuarioId = usuarioId;
            return this;
        }

        private Fila reversoDe(UUID originalId) {
            this.relacionadaId = originalId;
            this.motivo = "Reverso por error operativo";
            return this;
        }
    }
}
