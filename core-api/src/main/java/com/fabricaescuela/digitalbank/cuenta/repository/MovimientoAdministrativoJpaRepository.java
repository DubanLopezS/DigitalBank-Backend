package com.fabricaescuela.digitalbank.cuenta.repository;

import com.fabricaescuela.digitalbank.cuenta.entity.MovimientoAdministrativoCuenta;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.IMovimientoAdministrativoRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MovimientoAdministrativoJpaRepository
        extends JpaRepository<MovimientoAdministrativoCuenta, UUID>, IMovimientoAdministrativoRepository {
}