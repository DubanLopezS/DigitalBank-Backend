package com.fabricaescuela.digitalbank.cliente.interfaces.repositories;

import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;

import java.util.Optional;
import java.util.UUID;

public interface IClienteRepository {

    Cliente save(Cliente cliente);

    Optional<Cliente> findById(UUID id);

    boolean existsByTipoDocumentoAndNumeroDocumento(TipoDocumento tipoDocumento, String numeroDocumento);

    boolean existsByEmail(String email);

    Optional<Cliente> findByTipoDocumentoAndNumeroDocumento(TipoDocumento tipoDocumento, String numeroDocumento);
}
