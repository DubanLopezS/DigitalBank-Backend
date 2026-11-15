package com.fabricaescuela.digitalbank.cliente.repository;

import com.fabricaescuela.digitalbank.cliente.entity.AuditoriaDatosCliente;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.IAuditoriaDatosClienteRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditoriaDatosClienteJpaRepository
        extends JpaRepository<AuditoriaDatosCliente, UUID>, IAuditoriaDatosClienteRepository {
}
