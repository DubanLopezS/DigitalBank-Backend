package com.fabricaescuela.digitalbank.cliente.service;

import com.fabricaescuela.digitalbank.cliente.dto.InactivacionRegistrada;
import com.fabricaescuela.digitalbank.cliente.entity.EstadoCliente;
import com.fabricaescuela.digitalbank.cliente.entity.MovimientoAdministrativoCliente;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.IMovimientoAdministrativoClienteRepository;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteEstado;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class ClienteEstadoService implements IClienteEstado {

    private final JdbcClient jdbcClient;
    private final IMovimientoAdministrativoClienteRepository movimientoRepository;

    public ClienteEstadoService(JdbcClient jdbcClient,
                                IMovimientoAdministrativoClienteRepository movimientoRepository) {
        this.jdbcClient = jdbcClient;
        this.movimientoRepository = movimientoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estaInactivo(UUID clienteId) {
        return jdbcClient
                .sql("SELECT estado FROM cliente WHERE id = :id")
                .param("id", clienteId)
                .query((rs, rowNum) -> EstadoCliente.INACTIVO.name().equals(rs.getString("estado")))
                .optional()
                .orElse(false);
    }

    @Override
    @Transactional
    public Optional<EstadoCliente> bloquearYObtenerEstado(UUID clienteId) {
        return jdbcClient
                .sql("SELECT estado FROM cliente WHERE id = :id FOR UPDATE")
                .param("id", clienteId)
                .query((rs, rowNum) -> EstadoCliente.valueOf(rs.getString("estado")))
                .optional();
    }

    @Override
    @Transactional
    public InactivacionRegistrada inactivar(UUID clienteId, UUID usuarioId, String motivo) {
        jdbcClient
                .sql("UPDATE cliente SET estado = :estado WHERE id = :id")
                .param("estado", EstadoCliente.INACTIVO.name())
                .param("id", clienteId)
                .update();
        MovimientoAdministrativoCliente movimiento = movimientoRepository.save(
                MovimientoAdministrativoCliente.inactivacion(clienteId, usuarioId, motivo));
        return new InactivacionRegistrada(movimiento.getUsuarioId(), movimiento.getMotivo(),
                movimiento.getFechaHora());
    }
}
