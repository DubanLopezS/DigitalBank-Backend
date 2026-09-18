package com.fabricaescuela.digitalbank.cuenta.repository;

import com.fabricaescuela.digitalbank.cuenta.entity.Transaccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransaccionRepository extends JpaRepository<Transaccion, UUID> {
}
