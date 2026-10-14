package com.fabricaescuela.digitalbank.cuenta.service;


import com.fabricaescuela.digitalbank.cuenta.dto.CambioEstadoCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CambioEstadoCuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.MovimientoAdministrativoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoAccionCuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoDisponibleException;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.IMovimientoAdministrativoRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.IEstadoCuentaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EstadoCuentaServiceImpl implements IEstadoCuentaService {

    private final ICuentaRepository cuentaRepository;
    private final IMovimientoAdministrativoRepository movimientoRepository;

    public EstadoCuentaServiceImpl(ICuentaRepository cuentaRepository,
                                IMovimientoAdministrativoRepository movimientoRepository) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
    }

    @Override
    @Transactional
    public CambioEstadoCuentaResponse bloquear(UUID cuentaId, UUID usuarioId,
                                            CambioEstadoCuentaRequest request) {
        Cuenta cuenta = obtenerCuentaBloqueada(cuentaId);

        if (!cuenta.estaActiva()) {
            throw new CuentaNoDisponibleException("La cuenta no está activa");
        }

        EstadoCuenta estadoAnterior = cuenta.getEstado();
        cuenta.bloquear();
        return registrarCambio(cuenta, usuarioId, TipoAccionCuenta.BLOQUEO,
                estadoAnterior, request.motivo());
    }

    @Override
    @Transactional
    public CambioEstadoCuentaResponse desbloquear(UUID cuentaId, UUID usuarioId,
                                                CambioEstadoCuentaRequest request) {
        Cuenta cuenta = obtenerCuentaBloqueada(cuentaId);

        if (!cuenta.estaBloqueada()) {
            throw new CuentaNoDisponibleException("La cuenta no está bloqueada");
        }

        EstadoCuenta estadoAnterior = cuenta.getEstado();
        cuenta.desbloquear();
        return registrarCambio(cuenta, usuarioId, TipoAccionCuenta.DESBLOQUEO,
                estadoAnterior, request.motivo());
    }

    // Debe ser LO PRIMERO que se hace con la cuenta: findByIdForUpdate bloquea la fila
    // y trae el estado real. Si se leyera antes con findById, Hibernate podría devolver
    // una copia vieja. El ADMIN recibe 404 si no existe (ADR-0005).
    private Cuenta obtenerCuentaBloqueada(UUID cuentaId) {
        return cuentaRepository.findByIdForUpdate(cuentaId)
                .orElseThrow(CuentaNoEncontradaException::new);
    }

    // Guarda el nuevo estado y la auditoría en la misma transacción.
    private CambioEstadoCuentaResponse registrarCambio(Cuenta cuenta, UUID usuarioId,
                                                    TipoAccionCuenta accion,
                                                    EstadoCuenta estadoAnterior,
                                                    String motivo) {
        cuentaRepository.save(cuenta);

        MovimientoAdministrativoCuenta movimiento = movimientoRepository.save(
                MovimientoAdministrativoCuenta.registrar(cuenta.getId(), usuarioId, accion,
                        estadoAnterior, cuenta.getEstado(), motivo));

        return new CambioEstadoCuentaResponse(
                cuenta.getNumeroCuenta(),
                accion,
                estadoAnterior,
                cuenta.getEstado(),
                movimiento.getMotivo(),
                movimiento.getFechaHora(),
                movimiento.getUsuarioId());
    }
}