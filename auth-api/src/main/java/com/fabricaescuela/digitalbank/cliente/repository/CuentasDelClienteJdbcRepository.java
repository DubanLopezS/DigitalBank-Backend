package com.fabricaescuela.digitalbank.cliente.repository;

import com.fabricaescuela.digitalbank.cliente.dto.CuentaDelClienteResponse;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.ICuentasDelClienteRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

// Lee solo tres columnas de cuenta (sin dinero). Ordena por numero_cuenta
// para no pedir más columnas de las necesarias.
@Repository
public class CuentasDelClienteJdbcRepository implements ICuentasDelClienteRepository {

    private final JdbcClient jdbcClient;

    public CuentasDelClienteJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<CuentaDelClienteResponse> findByClienteId(UUID clienteId) {
        return jdbcClient.sql("""
                        SELECT numero_cuenta, tipo_cuenta, estado
                        FROM cuenta
                        WHERE cliente_id = :clienteId
                        ORDER BY numero_cuenta
                        """)
                .param("clienteId", clienteId)
                .query((rs, rowNum) -> new CuentaDelClienteResponse(
                        rs.getString("numero_cuenta"),
                        rs.getString("tipo_cuenta"),
                        rs.getString("estado")))
                .list();
    }
}