package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.auth.interfaces.services.IUsuarioQueryService;
import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.dto.SaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ISaldoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class SaldoServiceImpl implements ISaldoService {

    private static final String ROL_ADMIN = "ADMIN";
    private static final String ROL_CAJERO = "CAJERO";

    private final ICuentaRepository cuentaRepository;
    private final IUsuarioQueryService usuarioQueryService;

    public SaldoServiceImpl(ICuentaRepository cuentaRepository, IUsuarioQueryService usuarioQueryService) {
        this.cuentaRepository = cuentaRepository;
        this.usuarioQueryService = usuarioQueryService;
    }

    @Override
    @Transactional(readOnly = true)
    public SaldoResponse consultarSaldo(String numeroCuenta, UUID usuarioId, String rol) {
        boolean esPersonalAutorizado = ROL_ADMIN.equals(rol) || ROL_CAJERO.equals(rol);

        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> esPersonalAutorizado
                        ? new CuentaNoEncontradaException()
                        : new AccesoNoAutorizadoException());

        if (!esPersonalAutorizado) {
            validarTitularidad(cuenta, usuarioId);
        }

        BigDecimal saldoDisponible = cuenta.getSaldoContable().subtract(cuenta.getRetencion());

        return new SaldoResponse(cuenta.getNumeroCuenta(), cuenta.getSaldoContable(), saldoDisponible);
    }

    private void validarTitularidad(Cuenta cuenta, UUID usuarioId) {
        UUID clienteId = usuarioQueryService.obtenerClienteId(usuarioId)
                .orElseThrow(AccesoNoAutorizadoException::new);

        if (!clienteId.equals(cuenta.getClienteId())) {
            throw new AccesoNoAutorizadoException();
        }
    }
}
