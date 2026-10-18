package com.fabricaescuela.digitalbank.movimiento.repository;

import com.fabricaescuela.digitalbank.movimiento.dto.MovimientoResponse;
import com.fabricaescuela.digitalbank.movimiento.interfaces.repositories.IMovimientoRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MovimientoJdbcRepository implements IMovimientoRepository {

    // La cuenta relacionada sale de la otra fila con el mismo transferencia_id.
    // En depositos y retiros transferencia_id es NULL, asi que el LEFT JOIN no encuentra nada.
    private static final String SELECT_MOVIMIENTOS = """
            SELECT t.id, t.tipo, t.monto, t.fecha_hora, t.saldo_anterior, t.saldo_nuevo, t.estado,
                   t.origen, t.sentido, t.descripcion, t.transferencia_id,
                   c.numero_cuenta AS numero_cuenta_relacionada
            FROM transaccion t
            LEFT JOIN transaccion otra ON otra.transferencia_id = t.transferencia_id AND otra.id <> t.id
            LEFT JOIN cuenta c ON c.id = otra.cuenta_id
            """;

    private final JdbcClient jdbcClient;

    public MovimientoJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<UUID> findClienteIdByCuentaId(UUID cuentaId) {
        return jdbcClient
                .sql("SELECT cliente_id FROM cuenta WHERE id = :cuentaId")
                .param("cuentaId", cuentaId)
                .query((rs, rowNum) -> rs.getObject("cliente_id", UUID.class))
                .optional();
    }

    @Override
    public List<MovimientoResponse> findMovimientos(UUID cuentaId, LocalDate fechaInicio, LocalDate fechaFin,
                                                    String tipo, int page, int size) {
        return jdbcClient
                .sql(SELECT_MOVIMIENTOS + condiciones(fechaInicio, fechaFin, tipo)
                        + " ORDER BY t.fecha_hora DESC, t.id LIMIT :size OFFSET :offset")
                .param("cuentaId", cuentaId)
                .param("desde", inicioDelDia(fechaInicio))
                .param("hasta", inicioDelDiaSiguiente(fechaFin))
                .param("tipo", tipo)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(this::mapearMovimiento)
                .list();
    }

    @Override
    public long contarMovimientos(UUID cuentaId, LocalDate fechaInicio, LocalDate fechaFin, String tipo) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM transaccion t" + condiciones(fechaInicio, fechaFin, tipo))
                .param("cuentaId", cuentaId)
                .param("desde", inicioDelDia(fechaInicio))
                .param("hasta", inicioDelDiaSiguiente(fechaFin))
                .param("tipo", tipo)
                .query(Long.class)
                .single();
    }

    // Solo se concatenan textos fijos, los valores van como parametros nombrados
    private String condiciones(LocalDate fechaInicio, LocalDate fechaFin, String tipo) {
        StringBuilder where = new StringBuilder(" WHERE t.cuenta_id = :cuentaId");
        if (fechaInicio != null) {
            where.append(" AND t.fecha_hora >= :desde");
        }
        if (fechaFin != null) {
            where.append(" AND t.fecha_hora < :hasta");
        }
        if (tipo != null) {
            where.append(" AND t.tipo = :tipo");
        }
        return where.toString();
    }

    private LocalDateTime inicioDelDia(LocalDate fecha) {
        return fecha != null ? fecha.atStartOfDay() : null;
    }

    // fecha_hora es TIMESTAMP: se compara con "menor que el dia siguiente" para que entre todo fecha_fin
    private LocalDateTime inicioDelDiaSiguiente(LocalDate fecha) {
        return fecha != null ? fecha.plusDays(1).atStartOfDay() : null;
    }

    private MovimientoResponse mapearMovimiento(ResultSet rs, int rowNum) throws SQLException {
        return new MovimientoResponse(
                rs.getObject("id", UUID.class),
                rs.getString("tipo"),
                rs.getBigDecimal("monto"),
                rs.getObject("fecha_hora", LocalDateTime.class),
                rs.getBigDecimal("saldo_anterior"),
                rs.getBigDecimal("saldo_nuevo"),
                rs.getString("estado"),
                rs.getString("origen"),
                rs.getString("sentido"),
                rs.getString("descripcion"),
                rs.getObject("transferencia_id", UUID.class),
                rs.getString("numero_cuenta_relacionada")
        );
    }
}
