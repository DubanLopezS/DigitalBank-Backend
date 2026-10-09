package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteResumenResponse;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteConsulta;
import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.dto.AperturaCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.dto.MovimientoSaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoHabilitadoException;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoDisponibleException;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.cuenta.exception.SaldoInsuficienteException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

@Service
public class CuentaServiceImpl implements ICuentaService {

    private final ICuentaRepository cuentaRepository;
    private final IClienteConsulta clienteService;
    private final GeneradorNumeroCuenta generadorNumeroCuenta;

    public CuentaServiceImpl(ICuentaRepository cuentaRepository,
                            IClienteConsulta clienteService,
                            GeneradorNumeroCuenta generadorNumeroCuenta) {
        this.cuentaRepository = cuentaRepository;
        this.clienteService = clienteService;
        this.generadorNumeroCuenta = generadorNumeroCuenta;
    }

    @Override
    @Transactional
    public CuentaResponse abrirCuenta(AperturaCuentaRequest request, UUID clienteIdAutenticado) {

        ClienteResumenResponse cliente = clienteService
                .obtenerResumenPorDocumento(request.tipoDocumento(), request.numeroDocumento().trim())
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado"));

        if (!Objects.equals(clienteIdAutenticado, cliente.id())) {
            throw new ClienteNoAutorizadoException();
        }

        if (!cliente.activo()) {
            throw new ClienteNoHabilitadoException("Cliente no habilitado");
        }

        BigDecimal saldoInicial = (request.montoApertura() == null ? BigDecimal.ZERO : request.montoApertura())
                .setScale(2, RoundingMode.HALF_UP);

        Cuenta cuenta = new Cuenta(
                cliente.id(),
                generadorNumeroCuenta.generar(),
                request.tipoCuenta(),
                saldoInicial
        );

        return CuentaResponse.from(cuentaRepository.save(cuenta));
    }

    @Override
    @Transactional
    public MovimientoSaldoResponse acreditar(UUID cuentaId, BigDecimal monto) {
        validarMonto(monto);

        Cuenta cuenta = cuentaRepository.findByIdForUpdate(cuentaId)
                .orElseThrow(CuentaNoEncontradaException::new);

        validarCuentaDisponible(cuenta);

        BigDecimal saldoAnterior = cuenta.getSaldoContable();
        cuenta.acreditar(monto);
        Cuenta cuentaActualizada = cuentaRepository.save(cuenta);

        return new MovimientoSaldoResponse(
                cuentaActualizada.getId(),
                saldoAnterior,
                cuentaActualizada.getSaldoContable()
        );
    }

    @Override
    @Transactional
    public MovimientoSaldoResponse debitar(UUID cuentaId, BigDecimal monto) {
        validarMonto(monto);

        Cuenta cuenta = cuentaRepository.findByIdForUpdate(cuentaId)
                .orElseThrow(CuentaNoEncontradaException::new);

        if (!cuenta.estaActiva()) {
            throw new CuentaNoDisponibleException("Cuenta no disponible");
        }

        if (cuenta.getSaldoDisponible().compareTo(monto) < 0) {
            throw new SaldoInsuficienteException();
        }

        BigDecimal saldoAnterior = cuenta.getSaldoContable();
        cuenta.debitar(monto);
        Cuenta cuentaActualizada = cuentaRepository.save(cuenta);

        return new MovimientoSaldoResponse(
                cuentaActualizada.getId(),
                saldoAnterior,
                cuentaActualizada.getSaldoContable()
        );
    }

    @Override
    @Transactional
    public void validarTitularidad(UUID cuentaId, UUID clienteId) {
        Cuenta cuenta = cuentaRepository.findById(cuentaId)
                .orElseThrow(AccesoNoAutorizadoException::new);

        if (!Objects.equals(clienteId, cuenta.getClienteId())) {
            throw new AccesoNoAutorizadoException();
        }
    }

    private void validarCuentaDisponible(Cuenta cuenta) {
        if (cuenta.estaCerrada()) {
            throw new CuentaNoDisponibleException("Cuenta no disponible");
        }
    }

    private void validarMonto(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monto inválido");
        }
    }
}
