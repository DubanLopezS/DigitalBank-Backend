package com.fabricaescuela.digitalbank.cliente.interfaces.repositories;

import com.fabricaescuela.digitalbank.cliente.dto.CuentaDelClienteResponse;

import java.util.List;
import java.util.UUID;

public interface ICuentasDelClienteRepository {

    List<CuentaDelClienteResponse> findByClienteId(UUID clienteId);
}