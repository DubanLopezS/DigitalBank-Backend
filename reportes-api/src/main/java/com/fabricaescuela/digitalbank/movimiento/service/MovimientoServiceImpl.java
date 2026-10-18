package com.fabricaescuela.digitalbank.movimiento.service;

import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import com.fabricaescuela.digitalbank.movimiento.dto.HistorialMovimientosResponse;
import com.fabricaescuela.digitalbank.movimiento.dto.MovimientoResponse;
import com.fabricaescuela.digitalbank.movimiento.exception.CuentaNoEncontradaException;
import com.fabricaescuela.digitalbank.movimiento.exception.PaginacionInvalidaException;
import com.fabricaescuela.digitalbank.movimiento.exception.RangoFechasInvalidoException;
import com.fabricaescuela.digitalbank.movimiento.exception.TipoMovimientoInvalidoException;
import com.fabricaescuela.digitalbank.movimiento.interfaces.repositories.IMovimientoRepository;
import com.fabricaescuela.digitalbank.movimiento.interfaces.services.IMovimientoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MovimientoServiceImpl implements IMovimientoService {

    private static final String ROL_ADMIN = "ADMIN";
    private static final String ROL_CAJERO = "CAJERO";
    private static final int TAMANO_MAXIMO = 100;
    private static final Set<String> TIPOS_VALIDOS = Set.of("DEPOSITO", "RETIRO", "TRANSFERENCIA", "REVERSO");

    private final IMovimientoRepository movimientoRepository;

    public MovimientoServiceImpl(IMovimientoRepository movimientoRepository) {
        this.movimientoRepository = movimientoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public HistorialMovimientosResponse consultarMovimientos(UUID cuentaId, LocalDate fechaInicio, LocalDate fechaFin,
                                                             String tipo, int page, int size,
                                                             UUID clienteId, String rol) {
        validarFiltros(fechaInicio, fechaFin, tipo, page, size);

        boolean esPersonalAutorizado = ROL_ADMIN.equals(rol) || ROL_CAJERO.equals(rol);

        // ADR-0005: a un cliente no se le dice si la cuenta existe, recibe 403 igual que con una cuenta ajena
        UUID titularId = movimientoRepository.findClienteIdByCuentaId(cuentaId)
                .orElseThrow(() -> esPersonalAutorizado
                        ? new CuentaNoEncontradaException()
                        : new AccesoNoAutorizadoException());

        if (!esPersonalAutorizado && !titularId.equals(clienteId)) {
            throw new AccesoNoAutorizadoException();
        }

        List<MovimientoResponse> contenido = movimientoRepository.findMovimientos(
                cuentaId, fechaInicio, fechaFin, tipo, page, size);
        long total = movimientoRepository.contarMovimientos(cuentaId, fechaInicio, fechaFin, tipo);

        return new HistorialMovimientosResponse(contenido, page, size, total);
    }

    private void validarFiltros(LocalDate fechaInicio, LocalDate fechaFin, String tipo, int page, int size) {
        if (fechaInicio != null && fechaFin != null && fechaInicio.isAfter(fechaFin)) {
            throw new RangoFechasInvalidoException();
        }
        if (tipo != null && !TIPOS_VALIDOS.contains(tipo)) {
            throw new TipoMovimientoInvalidoException();
        }
        if (page < 0 || size < 1 || size > TAMANO_MAXIMO) {
            throw new PaginacionInvalidaException();
        }
    }
}
