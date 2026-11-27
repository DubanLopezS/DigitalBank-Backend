package com.fabricaescuela.digitalbank.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Triggers de la V7 sobre PostgreSQL")
class TriggersPostgresIntegrationTest extends IntegracionPostgresBase {

    private static final String MOTIVO = "'Movimiento registrado por error'";

    private Map<String, Object> parametros;
    private UUID depositoId;

    // Un cliente activo con dos cuentas, un depósito y una fila en cada tabla de auditoría.
    @BeforeEach
    void prepararDatos() {
        UUID clienteId = insertarCliente("1000000001", "ACTIVO");
        UUID cuentaId = insertarCuenta(clienteId, "1000000001", new BigDecimal("100000.00"));
        UUID otraCuentaId = insertarCuenta(clienteId, "1000000002", BigDecimal.ZERO);
        UUID usuarioId = UUID.randomUUID();
        depositoId = jdbc.sql("""
                        INSERT INTO transaccion (cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo, origen, descripcion)
                        VALUES (:cuentaId, 'DEPOSITO', 100000, 0, 100000, 'EFECTIVO', 'Depósito inicial')
                        RETURNING id
                        """)
                .param("cuentaId", cuentaId)
                .query(UUID.class)
                .single();
        parametros = Map.of(
                "clienteId", clienteId,
                "cuentaId", cuentaId,
                "otraCuentaId", otraCuentaId,
                "usuarioId", usuarioId,
                "depositoId", depositoId);
        ejecutar("""
                INSERT INTO auditoria_datos_cliente (cliente_id, usuario_id, campo, valor_anterior, valor_nuevo)
                VALUES (:clienteId, :usuarioId, 'EMAIL', 'antes@prueba.com', 'despues@prueba.com')
                """);
        ejecutar("""
                INSERT INTO movimiento_administrativo_cliente (cliente_id, usuario_id, estado_anterior, estado_nuevo, motivo)
                VALUES (:clienteId, :usuarioId, 'ACTIVO', 'INACTIVO', %s)
                """.formatted(MOTIVO));
        ejecutar("""
                INSERT INTO movimiento_administrativo_cuenta
                    (cuenta_id, usuario_id, tipo_accion, estado_anterior, estado_nuevo, motivo)
                VALUES (:cuentaId, :usuarioId, 'BLOQUEO', 'ACTIVA', 'BLOQUEADA', %s)
                """.formatted(MOTIVO));
    }

    @Test
    @DisplayName("La V7 crea los seis triggers")
    void existenLosTriggers() {
        List<String> triggers = jdbc.sql("SELECT tgname FROM pg_trigger WHERE NOT tgisinternal")
                .query(String.class)
                .list();

        assertThat(triggers).contains(
                "trg_aud_datos_cliente_inmutable", "trg_mov_admin_cliente_inmutable",
                "trg_mov_admin_cuenta_inmutable", "trg_transaccion_no_borrar",
                "trg_transaccion_proteger", "trg_cuenta_cliente_activo");
    }

    // ---- 1. Las tablas de auditoría no se editan ni se borran ----

    @ParameterizedTest(name = "{0} sobre {1}")
    @MethodSource("cambiosEnAuditoria")
    @DisplayName("Las tablas de auditoría rechazan UPDATE y DELETE y la fila queda igual")
    void auditoriaNoSeModificaNiSeBorra(String operacion, String tabla, String sql) {
        assertThatThrownBy(() -> ejecutar(sql))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("La tabla " + tabla + " es de auditoría: no se permite " + operacion);

        assertThat(jdbc.sql("SELECT count(*) FROM " + tabla).query(Integer.class).single()).isEqualTo(1);
    }

    static Stream<Arguments> cambiosEnAuditoria() {
        return Stream.of(
                arguments("UPDATE", "auditoria_datos_cliente",
                        "UPDATE auditoria_datos_cliente SET valor_nuevo = 'otro@prueba.com'"),
                arguments("DELETE", "auditoria_datos_cliente", "DELETE FROM auditoria_datos_cliente"),
                arguments("UPDATE", "movimiento_administrativo_cliente",
                        "UPDATE movimiento_administrativo_cliente SET motivo = 'Motivo cambiado después'"),
                arguments("DELETE", "movimiento_administrativo_cliente", "DELETE FROM movimiento_administrativo_cliente"),
                arguments("UPDATE", "movimiento_administrativo_cuenta",
                        "UPDATE movimiento_administrativo_cuenta SET motivo = 'Motivo cambiado después'"),
                arguments("DELETE", "movimiento_administrativo_cuenta", "DELETE FROM movimiento_administrativo_cuenta"));
    }

    // ---- 2. El historial de transacciones no se borra ----

    @Test
    @DisplayName("Una transacción no se puede borrar")
    void transaccionNoSeBorra() {
        assertThatThrownBy(() -> ejecutar("DELETE FROM transaccion WHERE id = :depositoId"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Las transacciones no se borran");

        assertThat(estadoDelDeposito()).isEqualTo("COMPLETADA");
    }

    // ---- 3. Una transacción solo puede pasar de COMPLETADA a REVERSADA ----

    @ParameterizedTest(name = "no se puede cambiar: {0}")
    @MethodSource("cambiosEnTransaccion")
    @DisplayName("Ningún dato de una transacción se puede modificar")
    void transaccionNoSeModifica(String cambio) {
        assertThatThrownBy(() -> ejecutar("UPDATE transaccion SET " + cambio + " WHERE id = :depositoId"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Una transacción no se modifica");
    }

    static Stream<String> cambiosEnTransaccion() {
        return Stream.of(
                "cuenta_id = :otraCuentaId",
                "tipo = 'RETIRO'",
                "monto = monto + 1",
                "saldo_anterior = 1",
                "saldo_nuevo = 1",
                "origen = 'CHEQUE'",
                "fecha_hora = fecha_hora + interval '1 day'",
                "transferencia_id = gen_random_uuid()",
                "sentido = 'ENTRANTE'",
                "descripcion = 'Descripción cambiada'",
                "usuario_id = gen_random_uuid()",
                "motivo = " + MOTIVO);
    }

    @Test
    @DisplayName("Una transacción COMPLETADA sí puede pasar a REVERSADA enlazada a su reverso")
    void completadaPuedePasarAReversada() {
        assertThatCode(this::reversarDeposito).doesNotThrowAnyException();

        assertThat(estadoDelDeposito()).isEqualTo("REVERSADA");
    }

    @Test
    @DisplayName("Una transacción REVERSADA no puede volver a COMPLETADA")
    void reversadaNoVuelveACompletada() {
        reversarDeposito();

        assertThatThrownBy(() -> ejecutar("UPDATE transaccion SET estado = 'COMPLETADA' WHERE id = :depositoId"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Cambio de estado no permitido: REVERSADA a COMPLETADA");
        assertThat(estadoDelDeposito()).isEqualTo("REVERSADA");
    }

    @Test
    @DisplayName("Una transacción COMPLETADA no se puede enlazar a otra sin pasar a REVERSADA")
    void completadaNoSeEnlazaSinReversar() {
        UUID otroDepositoId = insertarDeposito();

        assertThatThrownBy(() -> ejecutar(
                "UPDATE transaccion SET transaccion_relacionada_id = '%s' WHERE id = :depositoId".formatted(otroDepositoId)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("El enlace con el reverso solo se asigna al pasar a REVERSADA");
        assertThat(enlaceDelDeposito()).isNull();
    }

    @Test
    @DisplayName("Una transacción REVERSADA no se puede enlazar a otra transacción")
    void reversadaNoCambiaDeEnlace() {
        reversarDeposito();
        UUID reversoOriginal = enlaceDelDeposito();

        assertThatThrownBy(() -> ejecutar("UPDATE transaccion SET transaccion_relacionada_id = id WHERE id = :depositoId"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("El enlace con el reverso solo se asigna al pasar a REVERSADA");
        assertThat(enlaceDelDeposito()).isEqualTo(reversoOriginal);
    }

    @Test
    @DisplayName("Una transacción no pasa a REVERSADA apuntando a algo que no es un reverso")
    void reversadaDebeApuntarAUnReverso() {
        UUID otroDepositoId = insertarDeposito();

        assertThatThrownBy(() -> marcarDepositoReversadoCon(otroDepositoId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("La transacción solo puede pasar a REVERSADA enlazada a su REVERSO");
        assertThat(estadoDelDeposito()).isEqualTo("COMPLETADA");
    }

    @Test
    @DisplayName("Una transacción no pasa a REVERSADA apuntando al reverso de otra transacción")
    void reversadaDebeApuntarASuPropioReverso() {
        UUID otroDepositoId = insertarDeposito();
        UUID reversoDelOtro = jdbc.sql("""
                        INSERT INTO transaccion (cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo, sentido,
                                                 usuario_id, motivo, transaccion_relacionada_id)
                        VALUES (:cuentaId, 'REVERSO', 100000, 200000, 100000, 'SALIENTE', :usuarioId, %s, :otroId)
                        RETURNING id
                        """.formatted(MOTIVO))
                .params(parametros)
                .param("otroId", otroDepositoId)
                .query(UUID.class)
                .single();

        assertThatThrownBy(() -> marcarDepositoReversadoCon(reversoDelOtro))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("La transacción solo puede pasar a REVERSADA enlazada a su REVERSO");
        assertThat(estadoDelDeposito()).isEqualTo("COMPLETADA");
    }

    // ---- 4. Un cliente INACTIVO no recibe cuentas nuevas (HU-04) ----

    @Test
    @DisplayName("No se puede crear una cuenta para un cliente INACTIVO")
    void clienteInactivoNoRecibeCuentas() {
        UUID inactivoId = insertarCliente("2000000002", "INACTIVO");

        assertThatThrownBy(() -> insertarCuenta(inactivoId, "2000000002", BigDecimal.ZERO))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("El cliente está inactivo");
    }

    @Test
    @DisplayName("Un cliente ACTIVO sí puede recibir cuentas nuevas")
    void clienteActivoRecibeCuentas() {
        UUID activoId = insertarCliente("2000000002", "ACTIVO");

        assertThatCode(() -> insertarCuenta(activoId, "2000000002", BigDecimal.ZERO)).doesNotThrowAnyException();
    }

    private void reversarDeposito() {
        ejecutar("""
                WITH reverso AS (
                    INSERT INTO transaccion (cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo, sentido,
                                             usuario_id, motivo, transaccion_relacionada_id)
                    VALUES (:cuentaId, 'REVERSO', 100000, 100000, 0, 'SALIENTE', :usuarioId, %s, :depositoId)
                    RETURNING id)
                UPDATE transaccion SET estado = 'REVERSADA', transaccion_relacionada_id = (SELECT id FROM reverso)
                WHERE id = :depositoId
                """.formatted(MOTIVO));
    }

    private UUID insertarDeposito() {
        return jdbc.sql("""
                        INSERT INTO transaccion (cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo, origen, descripcion)
                        VALUES (:cuentaId, 'DEPOSITO', 100000, 100000, 200000, 'EFECTIVO', 'Otro depósito')
                        RETURNING id
                        """)
                .params(parametros)
                .query(UUID.class)
                .single();
    }

    private void marcarDepositoReversadoCon(UUID enlace) {
        ejecutar("UPDATE transaccion SET estado = 'REVERSADA', transaccion_relacionada_id = '%s' WHERE id = :depositoId"
                .formatted(enlace));
    }

    private UUID enlaceDelDeposito() {
        return jdbc.sql("SELECT transaccion_relacionada_id FROM transaccion WHERE id = :id").param("id", depositoId)
                .query(UUID.class).list().getFirst();
    }

    private String estadoDelDeposito() {
        return jdbc.sql("SELECT estado FROM transaccion WHERE id = :id").param("id", depositoId)
                .query(String.class).single();
    }

    private void ejecutar(String sql) {
        jdbc.sql(sql).params(parametros).update();
    }
}
