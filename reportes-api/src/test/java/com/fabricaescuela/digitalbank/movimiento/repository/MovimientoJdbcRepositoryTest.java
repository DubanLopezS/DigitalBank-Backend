package com.fabricaescuela.digitalbank.movimiento.repository;

import com.fabricaescuela.digitalbank.movimiento.dto.MovimientoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MovimientoJdbcRepository.class)
@Sql("/sql/esquema-movimientos.sql")
@DisplayName("MovimientoJdbcRepository - consultas sobre H2 en modo PostgreSQL")
class MovimientoJdbcRepositoryTest {

    private static final UUID CLIENTE_ID = UUID.randomUUID();

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private MovimientoJdbcRepository repository;

    @Test
    @DisplayName("Devuelve el cliente titular de la cuenta, aunque la cuenta este cerrada")
    void devuelveElTitularDeUnaCuentaCerrada() {
        UUID cuentaId = crearCuenta("1111111111", "CERRADA");

        assertThat(repository.findClienteIdByCuentaId(cuentaId)).contains(CLIENTE_ID);
    }

    @Test
    @DisplayName("Una cuenta que no existe devuelve vacio")
    void cuentaInexistenteDevuelveVacio() {
        assertThat(repository.findClienteIdByCuentaId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Los movimientos salen del mas reciente al mas antiguo y solo los de la cuenta")
    void ordenDescendenteYSoloDeLaCuenta() {
        UUID cuentaId = crearCuenta("1111111111", "ACTIVA");
        UUID otraCuenta = crearCuenta("2222222222", "ACTIVA");
        UUID antiguo = crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 1, 8, 0));
        UUID reciente = crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 3, 8, 0));
        UUID medio = crearRetiro(cuentaId, LocalDateTime.of(2026, 10, 2, 8, 0));
        crearDeposito(otraCuenta, LocalDateTime.of(2026, 10, 2, 9, 0));

        List<MovimientoResponse> movimientos = repository.findMovimientos(cuentaId, null, null, null, 0, 20);

        assertThat(movimientos).extracting(MovimientoResponse::id).containsExactly(reciente, medio, antiguo);
        assertThat(repository.contarMovimientos(cuentaId, null, null, null)).isEqualTo(3);
    }

    @Test
    @DisplayName("El filtro por fechas incluye todo el dia de fecha_fin y nada del dia siguiente")
    void filtroPorFechasIncluyeTodoElDiaFinal() {
        UUID cuentaId = crearCuenta("1111111111", "ACTIVA");
        crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 4, 23, 59, 59));
        UUID primeraHora = crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 5, 0, 0));
        UUID ultimoSegundo = crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 10, 23, 59, 59, 999_000_000));
        crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 11, 0, 0));
        LocalDate inicio = LocalDate.of(2026, 10, 5);
        LocalDate fin = LocalDate.of(2026, 10, 10);

        List<MovimientoResponse> movimientos = repository.findMovimientos(cuentaId, inicio, fin, null, 0, 20);

        assertThat(movimientos).extracting(MovimientoResponse::id).containsExactly(ultimoSegundo, primeraHora);
        assertThat(repository.contarMovimientos(cuentaId, inicio, fin, null)).isEqualTo(2);
    }

    @Test
    @DisplayName("Con la misma fecha de inicio y fin se traen los movimientos de ese dia")
    void mismaFechaTraeLosMovimientosDelDia() {
        UUID cuentaId = crearCuenta("1111111111", "ACTIVA");
        UUID delDia = crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 5, 15, 30));
        crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 6, 15, 30));
        LocalDate dia = LocalDate.of(2026, 10, 5);

        List<MovimientoResponse> movimientos = repository.findMovimientos(cuentaId, dia, dia, null, 0, 20);

        assertThat(movimientos).extracting(MovimientoResponse::id).containsExactly(delDia);
    }

    @Test
    @DisplayName("El filtro por tipo solo trae ese tipo de movimiento")
    void filtroPorTipo() {
        UUID cuentaId = crearCuenta("1111111111", "ACTIVA");
        crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 1, 8, 0));
        UUID retiro = crearRetiro(cuentaId, LocalDateTime.of(2026, 10, 2, 8, 0));

        List<MovimientoResponse> movimientos = repository.findMovimientos(cuentaId, null, null, "RETIRO", 0, 20);

        assertThat(movimientos).extracting(MovimientoResponse::id).containsExactly(retiro);
        assertThat(repository.contarMovimientos(cuentaId, null, null, "RETIRO")).isEqualTo(1);
    }

    @Test
    @DisplayName("Filtrar por REVERSO devuelve una lista vacia porque aun no hay filas de ese tipo")
    void filtroPorReversoDevuelveVacio() {
        UUID cuentaId = crearCuenta("1111111111", "ACTIVA");
        crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 1, 8, 0));

        assertThat(repository.findMovimientos(cuentaId, null, null, "REVERSO", 0, 20)).isEmpty();
        assertThat(repository.contarMovimientos(cuentaId, null, null, "REVERSO")).isZero();
    }

    @Test
    @DisplayName("La paginacion salta las paginas anteriores y el total cuenta todos los movimientos")
    void paginacion() {
        UUID cuentaId = crearCuenta("1111111111", "ACTIVA");
        UUID dia1 = crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 1, 8, 0));
        UUID dia2 = crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 2, 8, 0));
        UUID dia3 = crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 3, 8, 0));
        crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 4, 8, 0));
        crearDeposito(cuentaId, LocalDateTime.of(2026, 10, 5, 8, 0));

        List<MovimientoResponse> segundaPagina = repository.findMovimientos(cuentaId, null, null, null, 1, 2);
        List<MovimientoResponse> terceraPagina = repository.findMovimientos(cuentaId, null, null, null, 2, 2);

        assertThat(segundaPagina).extracting(MovimientoResponse::id).containsExactly(dia3, dia2);
        assertThat(terceraPagina).extracting(MovimientoResponse::id).containsExactly(dia1);
        assertThat(repository.contarMovimientos(cuentaId, null, null, null)).isEqualTo(5);
    }

    @Test
    @DisplayName("En una transferencia se ve el numero de la otra cuenta y en un deposito va null")
    void cuentaRelacionadaEnTransferencia() {
        UUID origen = crearCuenta("1111111111", "ACTIVA");
        UUID destino = crearCuenta("2222222222", "ACTIVA");
        UUID transferenciaId = UUID.randomUUID();
        LocalDateTime fecha = LocalDateTime.of(2026, 10, 5, 10, 0);
        crearTransferencia(origen, transferenciaId, "SALIENTE", fecha);
        crearTransferencia(destino, transferenciaId, "ENTRANTE", fecha);
        crearDeposito(origen, LocalDateTime.of(2026, 10, 1, 8, 0));

        List<MovimientoResponse> deOrigen = repository.findMovimientos(origen, null, null, null, 0, 20);
        List<MovimientoResponse> deDestino = repository.findMovimientos(destino, null, null, null, 0, 20);

        assertThat(deOrigen).hasSize(2);
        MovimientoResponse transferencia = deOrigen.get(0);
        assertThat(transferencia.tipo()).isEqualTo("TRANSFERENCIA");
        assertThat(transferencia.sentido()).isEqualTo("SALIENTE");
        assertThat(transferencia.transferenciaId()).isEqualTo(transferenciaId);
        assertThat(transferencia.descripcion()).isEqualTo("Pago arriendo");
        assertThat(transferencia.numeroCuentaRelacionada()).isEqualTo("2222222222");
        assertThat(transferencia.fechaHora()).isEqualTo(fecha);
        assertThat(transferencia.saldoNuevo()).isEqualByComparingTo("50000.00");

        MovimientoResponse deposito = deOrigen.get(1);
        assertThat(deposito.tipo()).isEqualTo("DEPOSITO");
        assertThat(deposito.origen()).isEqualTo("EFECTIVO");
        assertThat(deposito.transferenciaId()).isNull();
        assertThat(deposito.numeroCuentaRelacionada()).isNull();

        assertThat(deDestino).singleElement()
                .extracting(MovimientoResponse::numeroCuentaRelacionada)
                .isEqualTo("1111111111");
    }

    @Test
    @DisplayName("Una cuenta sin movimientos en el rango devuelve lista vacia y total cero")
    void sinMovimientosEnElRango() {
        UUID cuentaId = crearCuenta("1111111111", "ACTIVA");
        crearDeposito(cuentaId, LocalDateTime.of(2026, 9, 1, 8, 0));
        LocalDate inicio = LocalDate.of(2026, 10, 1);
        LocalDate fin = LocalDate.of(2026, 10, 31);

        assertThat(repository.findMovimientos(cuentaId, inicio, fin, null, 0, 20)).isEmpty();
        assertThat(repository.contarMovimientos(cuentaId, inicio, fin, null)).isZero();
    }

    private UUID crearCuenta(String numeroCuenta, String estado) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO cuenta (id, cliente_id, numero_cuenta, tipo_cuenta, saldo_contable, estado)
                        VALUES (:id, :clienteId, :numero, 'AHORROS', 100000.00, :estado)
                        """)
                .param("id", id)
                .param("clienteId", CLIENTE_ID)
                .param("numero", numeroCuenta)
                .param("estado", estado)
                .update();
        return id;
    }

    private UUID crearDeposito(UUID cuentaId, LocalDateTime fechaHora) {
        return insertarTransaccion(cuentaId, "DEPOSITO", "EFECTIVO", fechaHora, null, null, null);
    }

    private UUID crearRetiro(UUID cuentaId, LocalDateTime fechaHora) {
        return insertarTransaccion(cuentaId, "RETIRO", null, fechaHora, null, null, null);
    }

    private UUID crearTransferencia(UUID cuentaId, UUID transferenciaId, String sentido, LocalDateTime fechaHora) {
        return insertarTransaccion(cuentaId, "TRANSFERENCIA", null, fechaHora, transferenciaId, sentido, "Pago arriendo");
    }

    private UUID insertarTransaccion(UUID cuentaId, String tipo, String origen, LocalDateTime fechaHora,
                                     UUID transferenciaId, String sentido, String descripcion) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO transaccion (id, cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo, origen,
                                                 fecha_hora, transferencia_id, sentido, descripcion)
                        VALUES (:id, :cuentaId, :tipo, :monto, 100000.00, 50000.00, :origen,
                                :fechaHora, :transferenciaId, :sentido, :descripcion)
                        """)
                .param("id", id)
                .param("cuentaId", cuentaId)
                .param("tipo", tipo)
                .param("monto", new BigDecimal("50000.00"))
                .param("origen", origen)
                .param("fechaHora", fechaHora)
                .param("transferenciaId", transferenciaId)
                .param("sentido", sentido)
                .param("descripcion", descripcion)
                .update();
        return id;
    }
}
