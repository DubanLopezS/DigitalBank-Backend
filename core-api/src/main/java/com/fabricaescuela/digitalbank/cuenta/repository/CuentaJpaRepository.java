package com.fabricaescuela.digitalbank.cuenta.repository;

import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CuentaJpaRepository extends JpaRepository<Cuenta, UUID>, ICuentaRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.id = :id")
    Optional<Cuenta> findByIdForUpdate(@Param("id") UUID id);
}