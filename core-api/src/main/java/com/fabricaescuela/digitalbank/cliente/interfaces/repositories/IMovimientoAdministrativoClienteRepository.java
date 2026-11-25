package com.fabricaescuela.digitalbank.cliente.interfaces.repositories;

import com.fabricaescuela.digitalbank.cliente.entity.MovimientoAdministrativoCliente;

public interface IMovimientoAdministrativoClienteRepository {
    MovimientoAdministrativoCliente save(MovimientoAdministrativoCliente movimiento);
}
