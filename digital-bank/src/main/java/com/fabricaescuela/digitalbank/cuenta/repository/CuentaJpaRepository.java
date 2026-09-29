package com.fabricaescuela.digitalbank.cuenta.repository;

import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CuentaJpaRepository extends JpaRepository<Cuenta, UUID>, ICuentaRepository {
}