package com.fabricaescuela.digitalbank.cuenta.interfaces.repositories;

import com.fabricaescuela.digitalbank.cuenta.entity.MovimientoAdministrativoCuenta;

public interface IMovimientoAdministrativoRepository {
    MovimientoAdministrativoCuenta save(MovimientoAdministrativoCuenta movimiento);
}