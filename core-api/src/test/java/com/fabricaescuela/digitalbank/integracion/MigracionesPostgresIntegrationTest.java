package com.fabricaescuela.digitalbank.integracion;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Migraciones de Flyway sobre PostgreSQL")
class MigracionesPostgresIntegrationTest extends IntegracionPostgresBase {

    private static final String ESQUEMA_V5 = "prueba_guarda_v5";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @AfterEach
    void borrarEsquemaDePrueba() {
        jdbc.sql("DROP SCHEMA IF EXISTS " + ESQUEMA_V5 + " CASCADE").update();
    }

    @Test
    @DisplayName("Flyway aplica sobre una base vacía todas las migraciones del repositorio y ninguna queda fallida")
    void aplicaTodasLasMigraciones() throws Exception {
        // Se compara contra los archivos que hay en db/migration, así una migración nueva queda cubierta sola.
        List<String> enElRepositorio = Arrays.stream(new PathMatchingResourcePatternResolver()
                        .getResources("classpath:db/migration/V*__*.sql"))
                .map(recurso -> recurso.getFilename().substring(1, recurso.getFilename().indexOf("__")))
                .sorted(Comparator.comparingInt(Integer::parseInt))
                .toList();
        List<String> aplicadas = jdbc.sql("""
                        SELECT version FROM flyway_schema_history
                        WHERE success AND version IS NOT NULL
                        ORDER BY installed_rank
                        """)
                .query(String.class)
                .list();
        Integer fallidas = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE NOT success")
                .query(Integer.class)
                .single();

        assertThat(enElRepositorio).startsWith("1", "2", "3", "4", "5", "6");
        assertThat(aplicadas).isEqualTo(enElRepositorio);
        assertThat(fallidas).isZero();
    }

    @Test
    @DisplayName("Las entidades JPA cuadran con el esquema real: el contexto arranca con ddl-auto=validate")
    void lasEntidadesCuadranConElEsquema() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    @DisplayName("Existen todas las restricciones CHECK, UNIQUE y FK que definen las migraciones")
    void existenLasRestriccionesEsperadas() {
        List<String> restricciones = jdbc.sql("""
                        SELECT c.conname
                        FROM pg_constraint c
                        JOIN pg_namespace n ON n.oid = c.connamespace
                        WHERE n.nspname = 'public'
                        """)
                .query(String.class)
                .list();

        assertThat(restricciones).contains(
                "uk_cliente_documento", "uk_cliente_email", "ck_cliente_estado", "ck_cliente_email_minusculas",
                "uk_usuario_email", "uk_usuario_cliente", "ck_usuario_rol", "ck_usuario_estado_seg",
                "ck_usuario_email_minusculas",
                "uk_cuenta_numero", "ck_cuenta_saldo_no_negativo", "ck_cuenta_tipo", "ck_cuenta_estado",
                "ck_cuenta_retencion_no_negativa", "cuenta_cliente_id_fkey",
                "ck_transaccion_monto_positivo", "ck_transaccion_tipo", "ck_transaccion_origen",
                "ck_transaccion_estado", "ck_transaccion_sentido", "ck_transaccion_transferencia",
                "ck_transaccion_reverso", "ck_transaccion_reversada", "uk_transaccion_relacionada",
                "transaccion_cuenta_id_fkey", "transaccion_transaccion_relacionada_id_fkey",
                "ck_mov_admin_tipo_accion", "ck_mov_admin_estado_anterior", "ck_mov_admin_estado_nuevo",
                "ck_mov_admin_motivo",
                "ck_aud_datos_cliente_campo",
                "ck_mov_admin_cliente_estado_anterior", "ck_mov_admin_cliente_estado_nuevo",
                "ck_mov_admin_cliente_motivo");
    }

    @Test
    @DisplayName("V5 se detiene si hay correos que solo se diferencian en mayúsculas, sin borrar ni fusionar nada")
    void v5SeDetieneConCorreosRepetidos() {
        // Se migra hasta V4 en un esquema aparte para no tocar la base que usan las demás pruebas.
        Flyway hastaV4 = flywayEnEsquemaDePrueba().target("4").load();
        hastaV4.migrate();
        jdbc.sql("""
                INSERT INTO prueba_guarda_v5.cliente
                    (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento, email)
                VALUES ('CC', '1', 'Ana', 'Uno', DATE '1990-01-01', 'ana@prueba.com'),
                       ('CC', '2', 'Ana', 'Dos', DATE '1990-01-01', 'Ana@Prueba.com')
                """).update();

        Flyway completo = flywayEnEsquemaDePrueba().load();

        assertThatThrownBy(completo::migrate)
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("V5 detenida");
        List<String> correos = jdbc.sql("SELECT email FROM prueba_guarda_v5.cliente ORDER BY numero_documento")
                .query(String.class)
                .list();
        assertThat(correos).containsExactly("ana@prueba.com", "Ana@Prueba.com");
    }

    private FluentConfiguration flywayEnEsquemaDePrueba() {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(ESQUEMA_V5)
                .locations("classpath:db/migration");
    }
}
