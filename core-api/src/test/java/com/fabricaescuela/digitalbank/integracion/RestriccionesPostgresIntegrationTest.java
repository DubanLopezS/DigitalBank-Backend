package com.fabricaescuela.digitalbank.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Restricciones CHECK, UNIQUE y FK de las migraciones sobre PostgreSQL")
class RestriccionesPostgresIntegrationTest extends IntegracionPostgresBase {

    private static final String TX = """
            INSERT INTO transaccion (cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo, origen, estado,
                                     transferencia_id, sentido, usuario_id, motivo, transaccion_relacionada_id)
            VALUES (:cuentaId, %s)
            """;
    private static final String CLIENTE = """
            INSERT INTO cliente (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento, email, estado)
            VALUES (%s)
            """;
    private static final String USUARIO = """
            INSERT INTO usuario (cliente_id, email, password_hash, rol, estado_seguridad) VALUES (%s)
            """;
    private static final String CUENTA = """
            INSERT INTO cuenta (cliente_id, numero_cuenta, tipo_cuenta, saldo_contable, retencion, estado) VALUES (%s)
            """;
    private static final String MOV_CUENTA = """
            INSERT INTO movimiento_administrativo_cuenta
                (cuenta_id, usuario_id, tipo_accion, estado_anterior, estado_nuevo, motivo) VALUES (%s)
            """;
    private static final String MOV_CLIENTE = """
            INSERT INTO movimiento_administrativo_cliente
                (cliente_id, usuario_id, estado_anterior, estado_nuevo, motivo) VALUES (%s)
            """;
    private static final String MOTIVO = "'Movimiento registrado por error'";

    private Map<String, Object> parametros;

    // Datos válidos de base: un cliente con su usuario, una cuenta y un depósito.
    @BeforeEach
    void prepararDatos() {
        UUID clienteId = insertarCliente("1000000001", "ACTIVO");
        jdbc.sql(USUARIO.formatted(":clienteId, 'usuario@prueba.com', 'hash', 'CLIENTE', 'ACTIVO'"))
                .param("clienteId", clienteId).update();
        UUID cuentaId = insertarCuenta(clienteId, "1000000001", new BigDecimal("100000.00"));
        UUID depositoId = jdbc.sql((TX + " RETURNING id").formatted(
                        "'DEPOSITO', 100000, 0, 100000, 'EFECTIVO', 'COMPLETADA', NULL, NULL, NULL, NULL, NULL"))
                .param("cuentaId", cuentaId)
                .query(UUID.class)
                .single();
        parametros = Map.of(
                "clienteId", clienteId,
                "cuentaId", cuentaId,
                "depositoId", depositoId,
                "usuarioId", UUID.randomUUID(),
                "inexistenteId", UUID.randomUUID());
    }

    @ParameterizedTest(name = "{0} rechaza: {1}")
    @MethodSource("filasInvalidas")
    @DisplayName("PostgreSQL rechaza la fila inválida con la restricción esperada")
    void rechazaFilaInvalida(String restriccion, String caso, String sql) {
        assertThatThrownBy(() -> jdbc.sql(sql).params(parametros).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("\"" + restriccion + "\"");
    }

    @ParameterizedTest(name = "acepta: {0}")
    @MethodSource("filasValidas")
    @DisplayName("PostgreSQL acepta las filas válidas")
    void aceptaFilaValida(String caso, String sql) {
        assertThatCode(() -> jdbc.sql(sql).params(parametros).update()).doesNotThrowAnyException();
    }

    static Stream<Arguments> filasInvalidas() {
        return Stream.of(
                // cliente
                arguments("uk_cliente_documento", "documento repetido",
                        CLIENTE.formatted("'CC', '1000000001', 'Otro', 'Cliente', DATE '1990-01-01', 'otro@prueba.com', 'ACTIVO'")),
                arguments("uk_cliente_email", "correo repetido",
                        CLIENTE.formatted("'CC', '2000000002', 'Otro', 'Cliente', DATE '1990-01-01', '1000000001@prueba.com', 'ACTIVO'")),
                arguments("ck_cliente_estado", "estado fuera de la lista",
                        CLIENTE.formatted("'CC', '2000000002', 'Otro', 'Cliente', DATE '1990-01-01', 'otro@prueba.com', 'SUSPENDIDO'")),
                arguments("ck_cliente_email_minusculas", "correo con mayúsculas (V5)",
                        CLIENTE.formatted("'CC', '2000000002', 'Otro', 'Cliente', DATE '1990-01-01', 'Otro@Prueba.com', 'ACTIVO'")),
                // usuario
                arguments("uk_usuario_email", "correo de usuario repetido",
                        USUARIO.formatted("NULL, 'usuario@prueba.com', 'hash', 'ADMIN', 'ACTIVO'")),
                arguments("uk_usuario_cliente", "dos usuarios para el mismo cliente",
                        USUARIO.formatted(":clienteId, 'segundo@prueba.com', 'hash', 'CLIENTE', 'ACTIVO'")),
                arguments("ck_usuario_rol", "rol fuera de la lista",
                        USUARIO.formatted("NULL, 'jefe@prueba.com', 'hash', 'SUPERADMIN', 'ACTIVO'")),
                arguments("ck_usuario_estado_seg", "estado de seguridad fuera de la lista",
                        USUARIO.formatted("NULL, 'jefe@prueba.com', 'hash', 'ADMIN', 'BLOQUEADO'")),
                arguments("ck_usuario_email_minusculas", "correo de usuario con mayúsculas (V5)",
                        USUARIO.formatted("NULL, 'Jefe@Prueba.com', 'hash', 'ADMIN', 'ACTIVO'")),
                // cuenta
                arguments("uk_cuenta_numero", "número de cuenta repetido",
                        CUENTA.formatted(":clienteId, '1000000001', 'AHORROS', 0, 0, 'ACTIVA'")),
                arguments("ck_cuenta_saldo_no_negativo", "saldo negativo",
                        CUENTA.formatted(":clienteId, '2000000002', 'AHORROS', -0.01, 0, 'ACTIVA'")),
                arguments("ck_cuenta_tipo", "tipo de cuenta fuera de la lista",
                        CUENTA.formatted(":clienteId, '2000000002', 'NOMINA', 0, 0, 'ACTIVA'")),
                arguments("ck_cuenta_estado", "estado de cuenta fuera de la lista",
                        CUENTA.formatted(":clienteId, '2000000002', 'AHORROS', 0, 0, 'SUSPENDIDA'")),
                arguments("ck_cuenta_retencion_no_negativa", "retención negativa",
                        CUENTA.formatted(":clienteId, '2000000002', 'AHORROS', 0, -1, 'ACTIVA'")),
                arguments("cuenta_cliente_id_fkey", "cuenta de un cliente que no existe",
                        CUENTA.formatted(":inexistenteId, '2000000002', 'AHORROS', 0, 0, 'ACTIVA'")),
                // transaccion
                arguments("ck_transaccion_monto_positivo", "monto cero",
                        TX.formatted("'DEPOSITO', 0, 0, 0, 'EFECTIVO', 'COMPLETADA', NULL, NULL, NULL, NULL, NULL")),
                arguments("ck_transaccion_tipo", "tipo fuera de la lista",
                        TX.formatted("'PAGO', 10, 0, 10, NULL, 'COMPLETADA', NULL, NULL, NULL, NULL, NULL")),
                arguments("ck_transaccion_origen", "origen fuera de la lista",
                        TX.formatted("'DEPOSITO', 10, 0, 10, 'TARJETA', 'COMPLETADA', NULL, NULL, NULL, NULL, NULL")),
                arguments("ck_transaccion_estado", "estado FALLIDA, que no existe en la base",
                        TX.formatted("'DEPOSITO', 10, 0, 10, 'EFECTIVO', 'FALLIDA', NULL, NULL, NULL, NULL, NULL")),
                arguments("ck_transaccion_sentido", "sentido fuera de la lista",
                        TX.formatted("'TRANSFERENCIA', 10, 10, 0, NULL, 'COMPLETADA', gen_random_uuid(), 'LATERAL', NULL, NULL, NULL")),
                arguments("ck_transaccion_transferencia", "transferencia sin transferencia_id",
                        TX.formatted("'TRANSFERENCIA', 10, 10, 0, NULL, 'COMPLETADA', NULL, 'SALIENTE', NULL, NULL, NULL")),
                arguments("ck_transaccion_transferencia", "depósito con sentido",
                        TX.formatted("'DEPOSITO', 10, 0, 10, 'EFECTIVO', 'COMPLETADA', NULL, 'ENTRANTE', NULL, NULL, NULL")),
                arguments("ck_transaccion_transferencia", "reverso sin sentido",
                        TX.formatted("'REVERSO', 10, 10, 0, NULL, 'COMPLETADA', NULL, NULL, :usuarioId, " + MOTIVO + ", :depositoId")),
                arguments("ck_transaccion_reverso", "reverso sin motivo",
                        TX.formatted("'REVERSO', 10, 10, 0, NULL, 'COMPLETADA', NULL, 'SALIENTE', :usuarioId, NULL, :depositoId")),
                arguments("ck_transaccion_reverso", "reverso con motivo de menos de 10 caracteres sin contar espacios",
                        TX.formatted("'REVERSO', 10, 10, 0, NULL, 'COMPLETADA', NULL, 'SALIENTE', :usuarioId, '   corto     ', :depositoId")),
                arguments("ck_transaccion_reverso", "reverso sin usuario",
                        TX.formatted("'REVERSO', 10, 10, 0, NULL, 'COMPLETADA', NULL, 'SALIENTE', NULL, " + MOTIVO + ", :depositoId")),
                arguments("ck_transaccion_reverso", "reverso sin transacción original",
                        TX.formatted("'REVERSO', 10, 10, 0, NULL, 'COMPLETADA', NULL, 'SALIENTE', :usuarioId, " + MOTIVO + ", NULL")),
                arguments("ck_transaccion_reverso", "depósito con motivo (solo los reversos llevan motivo)",
                        TX.formatted("'DEPOSITO', 10, 0, 10, 'EFECTIVO', 'COMPLETADA', NULL, NULL, NULL, " + MOTIVO + ", NULL")),
                arguments("ck_transaccion_reversada", "un reverso no puede quedar REVERSADA",
                        TX.formatted("'REVERSO', 10, 10, 0, NULL, 'REVERSADA', NULL, 'SALIENTE', :usuarioId, " + MOTIVO + ", :depositoId")),
                arguments("ck_transaccion_reversada", "depósito REVERSADA sin apuntar a su reverso",
                        TX.formatted("'DEPOSITO', 10, 0, 10, 'EFECTIVO', 'REVERSADA', NULL, NULL, NULL, NULL, NULL")),
                arguments("transaccion_transaccion_relacionada_id_fkey", "reverso de una transacción que no existe",
                        TX.formatted("'REVERSO', 10, 10, 0, NULL, 'COMPLETADA', NULL, 'SALIENTE', :usuarioId, " + MOTIVO + ", :inexistenteId")),
                arguments("uk_transaccion_relacionada", "dos reversos de la misma transacción",
                        "WITH primero AS (" + TX.formatted(
                                "'REVERSO', 10, 10, 0, NULL, 'COMPLETADA', NULL, 'SALIENTE', :usuarioId, " + MOTIVO + ", :depositoId")
                                + " RETURNING id) " + TX.formatted(
                                "'REVERSO', 10, 10, 0, NULL, 'COMPLETADA', NULL, 'SALIENTE', :usuarioId, " + MOTIVO + ", :depositoId")),
                // movimiento_administrativo_cuenta
                arguments("ck_mov_admin_tipo_accion", "acción administrativa fuera de la lista",
                        MOV_CUENTA.formatted(":cuentaId, :usuarioId, 'ROBO', 'ACTIVA', 'BLOQUEADA', " + MOTIVO)),
                arguments("ck_mov_admin_estado_anterior", "estado anterior fuera de la lista",
                        MOV_CUENTA.formatted(":cuentaId, :usuarioId, 'BLOQUEO', 'SUSPENDIDA', 'BLOQUEADA', " + MOTIVO)),
                arguments("ck_mov_admin_estado_nuevo", "estado nuevo fuera de la lista",
                        MOV_CUENTA.formatted(":cuentaId, :usuarioId, 'BLOQUEO', 'ACTIVA', 'SUSPENDIDA', " + MOTIVO)),
                arguments("ck_mov_admin_motivo", "motivo de menos de 10 caracteres sin contar espacios",
                        MOV_CUENTA.formatted(":cuentaId, :usuarioId, 'BLOQUEO', 'ACTIVA', 'BLOQUEADA', '   corto     '")),
                // auditoria_datos_cliente y movimiento_administrativo_cliente (V6)
                arguments("ck_aud_datos_cliente_campo", "campo auditado fuera de la lista",
                        "INSERT INTO auditoria_datos_cliente (cliente_id, usuario_id, campo, valor_anterior, valor_nuevo) "
                                + "VALUES (:clienteId, :usuarioId, 'DIRECCION', 'a', 'b')"),
                arguments("ck_mov_admin_cliente_estado_anterior", "inactivar un cliente que no estaba ACTIVO",
                        MOV_CLIENTE.formatted(":clienteId, :usuarioId, 'INACTIVO', 'INACTIVO', " + MOTIVO)),
                arguments("ck_mov_admin_cliente_estado_nuevo", "pasar un cliente a un estado distinto de INACTIVO",
                        MOV_CLIENTE.formatted(":clienteId, :usuarioId, 'ACTIVO', 'ACTIVO', " + MOTIVO)),
                arguments("ck_mov_admin_cliente_motivo", "motivo de inactivación corto",
                        MOV_CLIENTE.formatted(":clienteId, :usuarioId, 'ACTIVO', 'INACTIVO', 'corto'")));
    }

    static Stream<Arguments> filasValidas() {
        return Stream.of(
                arguments("dos usuarios ADMIN sin cliente (UNIQUE permite varios NULL)",
                        "INSERT INTO usuario (cliente_id, email, password_hash, rol) "
                                + "VALUES (NULL, 'admin1@prueba.com', 'hash', 'ADMIN'), (NULL, 'admin2@prueba.com', 'hash', 'ADMIN')"),
                arguments("retiro sin origen",
                        TX.formatted("'RETIRO', 10, 100000, 99990, NULL, 'COMPLETADA', NULL, NULL, NULL, NULL, NULL")),
                arguments("las dos piernas de una transferencia",
                        "WITH t AS (SELECT gen_random_uuid() AS id) "
                                + "INSERT INTO transaccion (cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo, estado, transferencia_id, sentido) "
                                + "SELECT :cuentaId, 'TRANSFERENCIA', 10, 100000, 99990, 'COMPLETADA', t.id, 'SALIENTE' FROM t "
                                + "UNION ALL "
                                + "SELECT :cuentaId, 'TRANSFERENCIA', 10, 99990, 100000, 'COMPLETADA', t.id, 'ENTRANTE' FROM t"),
                arguments("reverso con motivo, usuario y transacción original",
                        TX.formatted("'REVERSO', 10, 10, 0, NULL, 'COMPLETADA', NULL, 'SALIENTE', :usuarioId, " + MOTIVO + ", :depositoId")),
                arguments("reverso que conserva el transferencia_id de la transferencia original",
                        TX.formatted("'REVERSO', 10, 10, 0, NULL, 'COMPLETADA', gen_random_uuid(), 'ENTRANTE', :usuarioId, " + MOTIVO + ", :depositoId")),
                arguments("depósito REVERSADA que apunta a su reverso",
                        "WITH r AS (" + TX.formatted(
                                "'REVERSO', 100000, 100000, 0, NULL, 'COMPLETADA', NULL, 'SALIENTE', :usuarioId, " + MOTIVO + ", :depositoId")
                                + " RETURNING id) "
                                + "UPDATE transaccion SET estado = 'REVERSADA', transaccion_relacionada_id = (SELECT id FROM r) "
                                + "WHERE id = :depositoId"));
    }
}
