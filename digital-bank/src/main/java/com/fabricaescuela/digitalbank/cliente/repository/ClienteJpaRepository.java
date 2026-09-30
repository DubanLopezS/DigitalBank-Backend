package com.fabricaescuela.digitalbank.cliente.repository;

import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.IClienteRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ClienteJpaRepository extends JpaRepository<Cliente, UUID>, IClienteRepository {
}