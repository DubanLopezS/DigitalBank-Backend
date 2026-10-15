package com.fabricaescuela.digitalbank.transaccion.service;

import com.fabricaescuela.digitalbank.cuenta.dto.MovimientoSaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaResponse;
import com.fabricaescuela.digitalbank.transaccion.entity.SentidoTransferencia;
import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;
import com.fabricaescuela.digitalbank.transaccion.exception.CuentaDestinoNoDisponibleException;
import com.fabricaescuela.digitalbank.transaccion.exception.CuentaDestinoNoEncontradaException;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.ITransferenciaService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransferenciaServiceImpl implements ITransferenciaService {

    private final ICuentaService cuentaService;
    private final ITransaccionRepository transaccionRepository;

    public TransferenciaServiceImpl(ICuentaService cuentaService, ITransaccionRepository transaccionRepository) {
        this.cuentaService = cuentaService;
        this.transaccionRepository = transaccionRepository;
    }

    @Override
    @Transactional
    public TransferenciaResponse registrarTransferencia(UUID cuentaId, TransferenciaRequest request, UUID clienteId) {
        cuentaService.validarTitularidad(cuentaId, clienteId);

        UUID destinoId = cuentaService.buscarIdPorNumero(request.cuentaDestino())
                .orElseThrow(CuentaDestinoNoEncontradaException::new);
        cuentaService.bloquearEnOrden(cuentaId, destinoId);

        validarCuentaDestinoDisponible(destinoId);

        MovimientoSaldoResponse salida = cuentaService.debitar(cuentaId, request.monto());
        MovimientoSaldoResponse entrada = cuentaService.acreditar(destinoId, request.monto());

        UUID transferenciaId = UUID.randomUUID();

        Transaccion transaccionSalida = Transaccion.transferencia(
                salida.cuentaId(),
                request.monto(),
                salida.saldoAnterior(),
                salida.saldoNuevo(),
                transferenciaId,
                SentidoTransferencia.SALIENTE,
                request.descripcion()
        );
        Transaccion transaccionEntrada = Transaccion.transferencia(
                entrada.cuentaId(),
                request.monto(),
                entrada.saldoAnterior(),
                entrada.saldoNuevo(),
                transferenciaId,
                SentidoTransferencia.ENTRANTE,
                request.descripcion()
        );
        Transaccion salidaGuardada = transaccionRepository.save(transaccionSalida);
        transaccionRepository.save(transaccionEntrada);

        return TransferenciaResponse.from(salidaGuardada, request.cuentaDestino());
    }

    private void validarCuentaDestinoDisponible(UUID destinoId) {
        if (cuentaService.estaCerrada(destinoId)) {
            throw new CuentaDestinoNoDisponibleException();
        }
    }
}
