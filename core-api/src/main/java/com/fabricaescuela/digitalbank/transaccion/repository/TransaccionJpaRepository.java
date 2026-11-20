package com.fabricaescuela.digitalbank.transaccion.repository;

import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransaccionJpaRepository extends JpaRepository<Transaccion, UUID>, ITransaccionRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaccion t where t.id = :id")
    Optional<Transaccion> findByIdForUpdate(@Param("id") UUID id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaccion t where t.transferenciaId = :transferenciaId order by t.id")
    List<Transaccion> findByTransferenciaIdForUpdate(@Param("transferenciaId") UUID transferenciaId);
}
