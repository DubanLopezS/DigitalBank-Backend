package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteConsulta;
import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResumenResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.entity.EstadoCuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cuenta.exception.FiltroInvalidoException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.repositories.ICuentaRepository;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.IListadoCuentasService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ListadoCuentasServiceImpl implements IListadoCuentasService {

    private static final Logger log = LoggerFactory.getLogger(ListadoCuentasServiceImpl.class);
    private static final String ROL_CLIENTE = "CLIENTE";

    private final ICuentaRepository cuentaRepository;
    private final IClienteConsulta clienteConsulta;

    public ListadoCuentasServiceImpl(ICuentaRepository cuentaRepository, IClienteConsulta clienteConsulta) {
        this.cuentaRepository = cuentaRepository;
        this.clienteConsulta = clienteConsulta;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuentaResumenResponse> listar(UUID clienteId, String estado,
                                            UUID clienteIdAutenticado, String rol) {
        boolean esCliente = ROL_CLIENTE.equals(rol);

        // ADR-0005: un CLIENTE con otro id recibe 403, exista o no ese cliente
        if (esCliente && !clienteId.equals(clienteIdAutenticado)) {
            throw new AccesoNoAutorizadoException();
        }

        EstadoCuenta filtro = interpretarEstado(estado);

        // ADMIN o CAJERO: 404 si el cliente no existe
        if (!esCliente && !clienteConsulta.existePorId(clienteId)) {
            throw new ClienteNoEncontradoException("Cliente no encontrado");
        }

        List<Cuenta> cuentas = filtro == null
                ? cuentaRepository.findByClienteIdOrderByFechaAperturaAsc(clienteId)
                : cuentaRepository.findByClienteIdAndEstadoOrderByFechaAperturaAsc(clienteId, filtro);

        log.info("evento=CUENTAS_LISTADAS clienteId={} cantidad={}", clienteId, cuentas.size());
        return cuentas.stream().map(CuentaResumenResponse::from).toList();
    }

    private EstadoCuenta interpretarEstado(String estado) {
        if (estado == null) {
            return null;
        }
        try {
            return EstadoCuenta.valueOf(estado.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new FiltroInvalidoException();
        }
    }
}