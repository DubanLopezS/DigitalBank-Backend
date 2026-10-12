package com.fabricaescuela.digitalbank.cuenta.interfaces.repositories;

import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;

import java.util.Optional;
import java.util.UUID;

public interface ICuentaRepository {

    Cuenta save(Cuenta cuenta);

    Optional<Cuenta> findById(UUID id);

    Optional<Cuenta> findByIdForUpdate(UUID id);

    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);

    Optional<UUID> findIdByNumeroCuenta(String numeroCuenta);

    boolean existsByNumeroCuenta(String numeroCuenta);

    boolean existeCuentaDelCliente(UUID cuentaId, UUID clienteId);
}