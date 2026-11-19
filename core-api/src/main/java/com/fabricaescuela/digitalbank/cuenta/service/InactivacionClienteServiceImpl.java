package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cliente.dto.InactivacionRegistrada;
import com.fabricaescuela.digitalbank.cliente.entity.EstadoCliente;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteEstado;
import com.fabricaescuela.digitalbank.cuenta.dto.InactivacionClienteRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.InactivacionClienteResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.MovimientoAdministrativoCuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.TipoAccionCuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteConSaldosPendientesException;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteYaInactivoException;
import com.fabricaescuela.digitalbank.cuenta.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.IMovimientoAdministrativoRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.IInactivacionClienteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InactivacionClienteServiceImpl implements IInactivacionClienteService {

    private static final Logger log = LoggerFactory.getLogger(InactivacionClienteServiceImpl.class);

    private final IClienteEstado clienteEstado;
    private final ICuentaRepository cuentaRepository;
    private final IMovimientoAdministrativoRepository movimientoCuentaRepository;

    public InactivacionClienteServiceImpl(IClienteEstado clienteEstado,
                                        ICuentaRepository cuentaRepository,
                                        IMovimientoAdministrativoRepository movimientoCuentaRepository) {
        this.clienteEstado = clienteEstado;
        this.cuentaRepository = cuentaRepository;
        this.movimientoCuentaRepository = movimientoCuentaRepository;
    }

    // Todo en una transacción: si algo falla, ni el cliente ni sus cuentas cambian.
    // Orden de bloqueo: primero la fila del cliente y después sus cuentas, ordenadas
    // igual que en bloquearEnOrden (ADR-0007). Los depósitos, retiros y transferencias
    // bloquean la cuenta y después leen el cliente sin bloquearlo, así que no se cruzan.
    @Override
    @Transactional
    public InactivacionClienteResponse inactivar(UUID clienteId, UUID usuarioId,
                                                InactivacionClienteRequest request) {
        EstadoCliente estado = clienteEstado.bloquearYObtenerEstado(clienteId)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado"));
        if (estado == EstadoCliente.INACTIVO) {
            throw new ClienteYaInactivoException();
        }

        List<Cuenta> cuentas = bloquearCuentasDelCliente(clienteId);
        if (cuentas.stream().anyMatch(cuenta -> !cuenta.tieneSaldoCero())) {
            throw new ClienteConSaldosPendientesException();
        }

        // Solo las cuentas ACTIVAS pasan a BLOQUEADA; las demás no cambian.
        List<String> cuentasBloqueadas = new ArrayList<>();
        for (Cuenta cuenta : cuentas) {
            if (cuenta.estaActiva()) {
                cuenta.bloquear();
                cuentaRepository.save(cuenta);
                movimientoCuentaRepository.save(MovimientoAdministrativoCuenta.registrar(
                        cuenta.getId(), usuarioId, TipoAccionCuenta.BLOQUEO,
                        EstadoCuenta.ACTIVA, EstadoCuenta.BLOQUEADA, request.motivo()));
                cuentasBloqueadas.add(cuenta.getNumeroCuenta());
            }
        }

        InactivacionRegistrada registro = clienteEstado.inactivar(clienteId, usuarioId, request.motivo());

        log.info("evento=CLIENTE_INACTIVADO clienteId={} usuarioId={} cuentasBloqueadas={}",
                clienteId, usuarioId, cuentasBloqueadas.size());

        return new InactivacionClienteResponse(
                clienteId,
                EstadoCliente.ACTIVO,
                EstadoCliente.INACTIVO,
                cuentasBloqueadas,
                registro.motivo(),
                registro.fechaHora(),
                registro.usuarioId());
    }

    private List<Cuenta> bloquearCuentasDelCliente(UUID clienteId) {
        List<UUID> ids = new ArrayList<>(cuentaRepository.findIdsByClienteId(clienteId));
        ids.sort(UUID::compareTo);
        List<Cuenta> cuentas = new ArrayList<>();
        for (UUID id : ids) {
            cuentas.add(cuentaRepository.findByIdForUpdate(id)
                    .orElseThrow(CuentaNoEncontradaException::new));
        }
        return cuentas;
    }
}
