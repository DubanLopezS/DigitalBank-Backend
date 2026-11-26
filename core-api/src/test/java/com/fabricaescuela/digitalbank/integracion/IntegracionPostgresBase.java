package com.fabricaescuela.digitalbank.integracion;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

// Pruebas contra un PostgreSQL real con las migraciones de Flyway aplicadas, a diferencia del
// resto de pruebas, que corren sobre H2 con Flyway apagado. Sin Docker se saltan en vez de fallar.
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@Import(PostgresTestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegracionPostgresBase {

    @Autowired
    protected JdbcClient jdbc;

    // Vacía todas las tablas de la aplicación (no el historial de Flyway), incluidas las que
    // agreguen migraciones futuras.
    @BeforeEach
    void limpiarBase() {
        jdbc.sql("""
                DO $$
                DECLARE tablas text;
                BEGIN
                    SELECT string_agg(format('%I', tablename), ', ') INTO tablas
                    FROM pg_tables
                    WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history';
                    EXECUTE 'TRUNCATE ' || tablas || ' CASCADE';
                END $$
                """).update();
    }

    protected UUID insertarCliente(String documento, String estado) {
        return jdbc.sql("""
                        INSERT INTO cliente (tipo_documento, numero_documento, nombres, apellidos,
                                             fecha_nacimiento, email, estado)
                        VALUES ('CC', :documento, 'Ana', 'Prueba', DATE '1990-01-01', :email, :estado)
                        RETURNING id
                        """)
                .param("documento", documento)
                .param("email", documento + "@prueba.com")
                .param("estado", estado)
                .query(UUID.class)
                .single();
    }

    protected UUID insertarCuenta(UUID clienteId, String numeroCuenta, BigDecimal saldo) {
        return jdbc.sql("""
                        INSERT INTO cuenta (cliente_id, numero_cuenta, tipo_cuenta, saldo_contable, estado)
                        VALUES (:clienteId, :numero, 'AHORROS', :saldo, 'ACTIVA')
                        RETURNING id
                        """)
                .param("clienteId", clienteId)
                .param("numero", numeroCuenta)
                .param("saldo", saldo)
                .query(UUID.class)
                .single();
    }

    protected BigDecimal saldoDe(UUID cuentaId) {
        return jdbc.sql("SELECT saldo_contable FROM cuenta WHERE id = :id")
                .param("id", cuentaId)
                .query(BigDecimal.class)
                .single();
    }
}
