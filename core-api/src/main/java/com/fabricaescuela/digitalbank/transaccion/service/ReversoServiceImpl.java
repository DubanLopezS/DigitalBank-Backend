package com.fabricaescuela.digitalbank.transaccion.service;

import com.fabricaescuela.digitalbank.cuenta.dto.MovimientoSaldoResponse;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;
import com.fabricaescuela.digitalbank.transaccion.dto.ReversoRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.ReversoResponse;
import com.fabricaescuela.digitalbank.transaccion.dto.ReversoResponse.MovimientoReverso;
import com.fabricaescuela.digitalbank.transaccion.entity.SentidoTransferencia;
import com.fabricaescuela.digitalbank.transaccion.entity.TipoTransaccion;
import com.fabricaescuela.digitalbank.transaccion.entity.Transaccion;
import com.fabricaescuela.digitalbank.transaccion.exception.TransaccionNoEncontradaException;
import com.fabricaescuela.digitalbank.transaccion.exception.TransaccionNoReversableException;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IReversoService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ReversoServiceImpl implements IReversoService {

    private static final Logger log = LoggerFactory.getLogger(ReversoServiceImpl.class);

    private final ICuentaService cuentaService;
    private final ITransaccionRepository transaccionRepository;

    public ReversoServiceImpl(ICuentaService cuentaService, ITransaccionRepository transaccionRepository) {
        this.cuentaService = cuentaService;
        this.transaccionRepository = transaccionRepository;
    }

    @Override
    @Transactional
    public ReversoResponse reversar(UUID transaccionId, ReversoRequest request, UUID usuarioId) {
        // Lectura sin bloqueo solo para decidir el tipo; el estado se revalida bajo bloqueo en cada rama.
        Transaccion original = transaccionRepository.findById(transaccionId)
                .orElseThrow(TransaccionNoEncontradaException::new);

        return switch (original.getTipo()) {
            case DEPOSITO -> reversarUnaCuenta(transaccionId, usuarioId, request.motivo(), false);
            case RETIRO -> reversarUnaCuenta(transaccionId, usuarioId, request.motivo(), true);
            case TRANSFERENCIA -> reversarTransferencia(original, usuarioId, request.motivo());
            // Un reverso no se puede reversar.
            default -> throw new TransaccionNoReversableException();
        };
    }

    // Depósito: se debita lo que se había acreditado. Retiro: se acredita lo que se había debitado.
    private ReversoResponse reversarUnaCuenta(UUID transaccionId, UUID usuarioId, String motivo, boolean credito) {
        Transaccion original = transaccionRepository.findByIdForUpdate(transaccionId)
                .orElseThrow(TransaccionNoEncontradaException::new);

        if (!original.estaCompletada()) {
            throw new TransaccionNoReversableException();
        }

        MovimientoSaldoResponse movimiento = cuentaService.aplicarReverso(
                original.getCuentaId(), original.getMonto(), credito);

        SentidoTransferencia sentido = credito ? SentidoTransferencia.ENTRANTE : SentidoTransferencia.SALIENTE;

        Transaccion reverso = Transaccion.reverso(
                original.getCuentaId(), original.getMonto(),
                movimiento.saldoAnterior(), movimiento.saldoNuevo(),
                sentido, null, original.getId(), usuarioId, motivo);
        Transaccion reversoGuardado = transaccionRepository.save(reverso);

        original.marcarReversada(reversoGuardado.getId());
        transaccionRepository.save(original);

        log.info("evento=REVERSO_REGISTRADO tipoOriginal={} transaccionOriginalId={} reversoId={} usuarioId={} monto={}",
                original.getTipo(), original.getId(), reversoGuardado.getId(), usuarioId, original.getMonto());

        return new ReversoResponse(
                original.getId(), original.getTipo().name(), "REVERSADA",
                original.getMonto(), motivo, usuarioId, reversoGuardado.getFechaHora(),
                List.of(MovimientoReverso.from(reversoGuardado)));
    }

    // Transferencia: se devuelve el dinero al origen y se descuenta del destino. Son sus dos piernas.
    private ReversoResponse reversarTransferencia(Transaccion original, UUID usuarioId, String motivo) {
        List<Transaccion> piernas = transaccionRepository
                .findByTransferenciaIdForUpdate(original.getTransferenciaId());

        Transaccion salida = buscarPierna(piernas, SentidoTransferencia.SALIENTE);
        Transaccion entrada = buscarPierna(piernas, SentidoTransferencia.ENTRANTE);

        if (!salida.estaCompletada() || !entrada.estaCompletada()) {
            throw new TransaccionNoReversableException();
        }

        UUID cuentaOrigenId = salida.getCuentaId();
        UUID cuentaDestinoId = entrada.getCuentaId();

        cuentaService.bloquearEnOrden(cuentaOrigenId, cuentaDestinoId);

        MovimientoSaldoResponse movimientoOrigen = cuentaService.aplicarReverso(cuentaOrigenId, original.getMonto(), true);
        MovimientoSaldoResponse movimientoDestino = cuentaService.aplicarReverso(cuentaDestinoId, original.getMonto(), false);

        // El reverso conserva el transferencia_id de la transferencia original para quedar ligado a ella;
        // tipo=REVERSO distingue las dos filas del reverso de las dos de la transferencia.
        UUID transferenciaId = original.getTransferenciaId();

        Transaccion reversoOrigen = Transaccion.reverso(
                cuentaOrigenId, original.getMonto(),
                movimientoOrigen.saldoAnterior(), movimientoOrigen.saldoNuevo(),
                SentidoTransferencia.ENTRANTE, transferenciaId, salida.getId(), usuarioId, motivo);
        Transaccion reversoDestino = Transaccion.reverso(
                cuentaDestinoId, original.getMonto(),
                movimientoDestino.saldoAnterior(), movimientoDestino.saldoNuevo(),
                SentidoTransferencia.SALIENTE, transferenciaId, entrada.getId(), usuarioId, motivo);

        Transaccion reversoOrigenGuardado = transaccionRepository.save(reversoOrigen);
        Transaccion reversoDestinoGuardado = transaccionRepository.save(reversoDestino);

        salida.marcarReversada(reversoOrigenGuardado.getId());
        entrada.marcarReversada(reversoDestinoGuardado.getId());
        transaccionRepository.save(salida);
        transaccionRepository.save(entrada);

        log.info("evento=REVERSO_REGISTRADO tipoOriginal=TRANSFERENCIA transaccionOriginalId={} transferenciaId={} "
                        + "reversoOrigenId={} reversoDestinoId={} usuarioId={} monto={}",
                original.getId(), original.getTransferenciaId(), reversoOrigenGuardado.getId(),
                reversoDestinoGuardado.getId(), usuarioId, original.getMonto());

        return new ReversoResponse(
                original.getId(), original.getTipo().name(), "REVERSADA",
                original.getMonto(), motivo, usuarioId, reversoOrigenGuardado.getFechaHora(),
                List.of(MovimientoReverso.from(reversoOrigenGuardado), MovimientoReverso.from(reversoDestinoGuardado)));
    }

    // Solo las piernas de la transferencia original: con el mismo transferencia_id también quedan
    // las dos filas REVERSO, que no se deben confundir con las originales.
    private Transaccion buscarPierna(List<Transaccion> piernas, SentidoTransferencia sentido) {
        return piernas.stream()
                .filter(t -> t.getTipo() == TipoTransaccion.TRANSFERENCIA && t.getSentido() == sentido)
                .findFirst()
                .orElseThrow(TransaccionNoReversableException::new);
    }
}
