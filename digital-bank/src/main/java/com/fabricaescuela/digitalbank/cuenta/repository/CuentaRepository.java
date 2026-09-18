package com.fabricaescuela.digitalbank.cuenta.repository;

import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CuentaRepository extends JpaRepository<Cuenta, UUID> {
}
