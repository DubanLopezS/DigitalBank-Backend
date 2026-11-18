package com.fabricaescuela.digitalbank.transaccion.repository;

import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccion;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccion.CuentaTransaccion;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TransaccionJdbcRepository implements ITransaccionRepository {

    // De cuenta solo se leen id, cliente_id y numero_cuenta.
    // En una transferencia la otra cuenta sale de la otra fila con el mismo transferencia_id.
    // Las dos filas deben ser TRANSFERENCIA: si un reverso usara el mismo transferencia_id,
    // sin ese filtro la consulta devolveria dos filas.
    private static final String SELECT_DETALLE = """
            SELECT t.id, t.tipo, t.monto, t.estado, t.fecha_hora, t.origen, t.sentido, t.descripcion,
                   t.usuario_id, t.transaccion_relacionada_id,
                   c.numero_cuenta, c.cliente_id,
                   oc.numero_cuenta AS otra_numero_cuenta, oc.cliente_id AS otra_cliente_id
            FROM transaccion t
            JOIN cuenta c ON c.id = t.cuenta_id
            LEFT JOIN transaccion otra ON t.tipo = 'TRANSFERENCIA' AND otra.tipo = 'TRANSFERENCIA'
                 AND otra.transferencia_id = t.transferencia_id AND otra.id <> t.id
            LEFT JOIN cuenta oc ON oc.id = otra.cuenta_id
            WHERE t.id = :transaccionId
            """;

    private final JdbcClient jdbcClient;

    public TransaccionJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<DetalleTransaccion> findDetalleById(UUID transaccionId) {
        return jdbcClient
                .sql(SELECT_DETALLE)
                .param("transaccionId", transaccionId)
                .query(this::mapearDetalle)
                .optional();
    }

    private DetalleTransaccion mapearDetalle(ResultSet rs, int rowNum) throws SQLException {
        CuentaTransaccion cuentaDeLaFila = new CuentaTransaccion(
                rs.getString("numero_cuenta"), rs.getObject("cliente_id", UUID.class));
        String otraNumeroCuenta = rs.getString("otra_numero_cuenta");
        CuentaTransaccion otraCuenta = otraNumeroCuenta != null
                ? new CuentaTransaccion(otraNumeroCuenta, rs.getObject("otra_cliente_id", UUID.class))
                : null;
        boolean laFilaEsOrigen = esCuentaOrigen(rs.getString("tipo"), rs.getString("sentido"));

        return new DetalleTransaccion(
                rs.getObject("id", UUID.class),
                rs.getString("tipo"),
                rs.getBigDecimal("monto"),
                rs.getString("estado"),
                rs.getObject("fecha_hora", LocalDateTime.class),
                laFilaEsOrigen ? cuentaDeLaFila : otraCuenta,
                laFilaEsOrigen ? otraCuenta : cuentaDeLaFila,
                rs.getObject("transaccion_relacionada_id", UUID.class),
                rs.getString("origen"),
                rs.getString("descripcion"),
                rs.getObject("usuario_id", UUID.class)
        );
    }

    // DEPOSITO: la plata entra a la cuenta de la fila, que es el destino.
    // RETIRO: la plata sale de la cuenta de la fila, que es el origen.
    // TRANSFERENCIA y REVERSO: SALIENTE indica que sale de la cuenta de la fila y ENTRANTE que entra.
    private boolean esCuentaOrigen(String tipo, String sentido) {
        return switch (tipo) {
            case "DEPOSITO" -> false;
            case "RETIRO" -> true;
            default -> "SALIENTE".equals(sentido);
        };
    }
}
