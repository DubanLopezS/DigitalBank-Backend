package com.fabricaescuela.digitalbank.cliente.interfaces.repositories;

import com.fabricaescuela.digitalbank.cliente.entity.AuditoriaDatosCliente;

public interface IAuditoriaDatosClienteRepository {

    AuditoriaDatosCliente save(AuditoriaDatosCliente auditoria);
}
