package com.fabricaescuela.digitalbank.integracion;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// El contenedor vive con el contexto de Spring: las clases de prueba que comparten contexto
// reutilizan el mismo PostgreSQL. Misma versión que el docker-compose del proyecto.
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
    }
}
