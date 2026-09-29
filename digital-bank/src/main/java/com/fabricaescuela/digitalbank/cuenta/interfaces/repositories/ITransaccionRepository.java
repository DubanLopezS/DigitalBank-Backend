package com.fabricaescuela.digitalbank.cuenta.interfaces.repositories;

import com.fabricaescuela.digitalbank.cuenta.entity.Transaccion;

public interface ITransaccionRepository {

    Transaccion save(Transaccion transaccion);
}