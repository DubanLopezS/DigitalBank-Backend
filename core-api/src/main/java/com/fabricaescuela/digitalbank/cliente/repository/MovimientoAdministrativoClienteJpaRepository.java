package com.fabricaescuela.digitalbank.cliente.repository;

import com.fabricaescuela.digitalbank.cliente.entity.MovimientoAdministrativoCliente;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.IMovimientoAdministrativoClienteRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MovimientoAdministrativoClienteJpaRepository
        extends JpaRepository<MovimientoAdministrativoCliente, UUID>, IMovimientoAdministrativoClienteRepository {
}
