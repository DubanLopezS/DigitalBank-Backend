package com.fabricaescuela.digitalbank.transaccion.service;

import com.fabricaescuela.digitalbank.cuenta.dto.MovimientoSaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;
import com.fabricaescuela.digitalbank.transaccion.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IDepositoService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DepositoServiceImpl implements IDepositoService {

    private final ICuentaService cuentaService;
    private final ITransaccionRepository transaccionRepository;

    public DepositoServiceImpl(ICuentaService cuentaService, ITransaccionRepository transaccionRepository) {
        this.cuentaService = cuentaService;
        this.transaccionRepository = transaccionRepository;
    }

    @Override
    @Transactional
    public TransaccionResponse registrarDeposito(UUID cuentaId, DepositoRequest request) {
        MovimientoSaldoResponse movimiento = cuentaService.acreditar(cuentaId, request.monto());

        Transaccion transaccion = Transaccion.deposito(
                movimiento.cuentaId(),
                request.monto(),
                movimiento.saldoAnterior(),
                movimiento.saldoNuevo(),
                request.origen()
        );
        Transaccion transaccionGuardada = transaccionRepository.save(transaccion);

        return TransaccionResponse.from(transaccionGuardada);
    }
}
