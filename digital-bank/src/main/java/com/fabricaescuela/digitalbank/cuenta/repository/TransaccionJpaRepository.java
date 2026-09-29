package com.fabricaescuela.digitalbank.cuenta.repository;

import com.fabricaescuela.digitalbank.cuenta.entity.Transaccion;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ITransaccionRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransaccionJpaRepository extends JpaRepository<Transaccion, UUID>, ITransaccionRepository {
}