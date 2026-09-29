package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cuenta.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.TransaccionResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.Transaccion;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoDisponibleException;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ITransaccionRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.IDepositoService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class DepositoServiceImpl implements IDepositoService {

    private final ICuentaRepository cuentaRepository;
    private final ITransaccionRepository transaccionRepository;

    public DepositoServiceImpl(ICuentaRepository cuentaRepository, ITransaccionRepository transaccionRepository) {
        this.cuentaRepository = cuentaRepository;
        this.transaccionRepository = transaccionRepository;
    }

    @Override
    @Transactional
    public TransaccionResponse registrarDeposito(UUID cuentaId, DepositoRequest request) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(CuentaNoEncontradaException::new);

        validarCuentaDisponible(cuenta);

        BigDecimal saldoAnterior = cuenta.getSaldoContable();
        cuenta.acreditar(request.monto());
        Cuenta cuentaActualizada = cuentaRepository.save(cuenta);

        Transaccion transaccion = Transaccion.deposito(
                cuentaActualizada.getId(),
                request.monto(),
                saldoAnterior,
                cuentaActualizada.getSaldoContable(),
                request.origen()
        );
        Transaccion transaccionGuardada = transaccionRepository.save(transaccion);

        return TransaccionResponse.from(transaccionGuardada);
    }

    private void validarCuentaDisponible(Cuenta cuenta) {
        if (cuenta.estaCerrada()) {
            throw new CuentaNoDisponibleException("Cuenta no disponible");
        }
    }
}
