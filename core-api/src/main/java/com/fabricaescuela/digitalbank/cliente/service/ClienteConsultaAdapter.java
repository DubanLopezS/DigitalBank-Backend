package com.fabricaescuela.digitalbank.cliente.service;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteResumenResponse;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteConsulta;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementación de solo lectura de IClienteService para core-api.
 * Consulta directamente la tabla cliente (base compartida, ADR 0009).
 */
@Service
public class ClienteConsultaAdapter implements IClienteConsulta {

    private final JdbcClient jdbcClient;

    public ClienteConsultaAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClienteResumenResponse> obtenerResumenPorDocumento(TipoDocumento tipoDocumento,
                                                                    String numeroDocumento) {
        return jdbcClient
                .sql("SELECT id, estado FROM cliente WHERE tipo_documento = :tipo AND numero_documento = :numero")
                .param("tipo", tipoDocumento.name())
                .param("numero", numeroDocumento)
                .query((rs, rowNum) -> new ClienteResumenResponse(
                        rs.getObject("id", UUID.class),
                        "ACTIVO".equals(rs.getString("estado"))))
                .optional();
    }
}