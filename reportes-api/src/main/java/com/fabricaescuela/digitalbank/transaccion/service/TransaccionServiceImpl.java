package com.fabricaescuela.digitalbank.transaccion.service;

import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccion;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccion.CuentaTransaccion;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccionAdminResponse;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccionClienteResponse;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccionResponse;
import com.fabricaescuela.digitalbank.transaccion.exception.TransaccionNoEncontradaException;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.ITransaccionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class TransaccionServiceImpl implements ITransaccionService {

    private static final String ROL_ADMIN = "ADMIN";

    private final ITransaccionRepository transaccionRepository;

    public TransaccionServiceImpl(ITransaccionRepository transaccionRepository) {
        this.transaccionRepository = transaccionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DetalleTransaccionResponse consultarDetalle(UUID transaccionId, UUID clienteId, String rol) {
        Optional<DetalleTransaccion> encontrada = transaccionRepository.findDetalleById(transaccionId);

        if (ROL_ADMIN.equals(rol)) {
            return DetalleTransaccionAdminResponse.from(
                    encontrada.orElseThrow(TransaccionNoEncontradaException::new));
        }

        // ADR-0005: a un cliente no se le dice si la transaccion existe, recibe 403 igual que con una ajena
        DetalleTransaccion detalle = encontrada
                .filter(transaccion -> esDelCliente(transaccion, clienteId))
                .orElseThrow(AccesoNoAutorizadoException::new);
        return DetalleTransaccionClienteResponse.from(detalle);
    }

    private boolean esDelCliente(DetalleTransaccion transaccion, UUID clienteId) {
        return clienteId != null
                && (esTitular(transaccion.cuentaOrigen(), clienteId)
                || esTitular(transaccion.cuentaDestino(), clienteId));
    }

    private boolean esTitular(CuentaTransaccion cuenta, UUID clienteId) {
        return cuenta != null && clienteId.equals(cuenta.clienteId());
    }
}
