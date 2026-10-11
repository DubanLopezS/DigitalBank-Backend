package com.fabricaescuela.digitalbank.transaccion.service;

import com.fabricaescuela.digitalbank.cuenta.dto.MovimientoSaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;
import com.fabricaescuela.digitalbank.transaccion.dto.RetiroRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IRetiroService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RetiroServiceImpl implements IRetiroService {

    private final ICuentaService cuentaService;
    private final ITransaccionRepository transaccionRepository;

    public RetiroServiceImpl(ICuentaService cuentaService, ITransaccionRepository transaccionRepository) {
        this.cuentaService = cuentaService;
        this.transaccionRepository = transaccionRepository;
    }

    @Override
    @Transactional
    public TransaccionResponse registrarRetiro(UUID cuentaId, RetiroRequest request, UUID clienteId) {
        cuentaService.validarTitularidad(cuentaId, clienteId);

        MovimientoSaldoResponse movimiento = cuentaService.debitar(cuentaId, request.monto());

        Transaccion transaccion = Transaccion.retiro(
                movimiento.cuentaId(),
                request.monto(),
                movimiento.saldoAnterior(),
                movimiento.saldoNuevo()
        );
        Transaccion transaccionGuardada = transaccionRepository.save(transaccion);

        return TransaccionResponse.from(transaccionGuardada);
    }
}
