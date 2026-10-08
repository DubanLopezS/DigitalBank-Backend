package com.fabricaescuela.digitalbank.transaccion.repository;

import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransaccionJpaRepository extends JpaRepository<Transaccion, UUID>, ITransaccionRepository {
}