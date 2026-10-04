package com.fabricaescuela.digitalbank.transaccion.interfaces.repositories;

import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;

public interface ITransaccionRepository {

    Transaccion save(Transaccion transaccion);
}