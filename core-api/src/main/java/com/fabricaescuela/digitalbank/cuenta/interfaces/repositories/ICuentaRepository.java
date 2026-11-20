package com.fabricaescuela.digitalbank.cuenta.interfaces.repositories;

import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;

import java.util.List;
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

    List<Cuenta> findByClienteIdOrderByFechaAperturaAsc(UUID clienteId);

    List<Cuenta> findByClienteIdAndEstadoOrderByFechaAperturaAsc(UUID clienteId, EstadoCuenta estado);
}