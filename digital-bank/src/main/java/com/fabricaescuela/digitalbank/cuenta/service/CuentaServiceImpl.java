package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
import com.fabricaescuela.digitalbank.cliente.entity.EstadoCliente;
import com.fabricaescuela.digitalbank.cliente.repository.ClienteRepository;
import com.fabricaescuela.digitalbank.cuenta.dto.AperturaCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResponse;
import com.fabricaescuela.digitalbank.cuenta.entity.Cuenta;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoHabilitadoException;
import com.fabricaescuela.digitalbank.cuenta.interfaces.CuentaService;
import com.fabricaescuela.digitalbank.cuenta.repository.CuentaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CuentaServiceImpl implements CuentaService {

    private final CuentaRepository cuentaRepository;
    private final ClienteRepository clienteRepository;
    private final GeneradorNumeroCuenta generadorNumeroCuenta;

    public CuentaServiceImpl(CuentaRepository cuentaRepository,
                             ClienteRepository clienteRepository,
                             GeneradorNumeroCuenta generadorNumeroCuenta) {
        this.cuentaRepository = cuentaRepository;
        this.clienteRepository = clienteRepository;
        this.generadorNumeroCuenta = generadorNumeroCuenta;
    }

    @Override
    @Transactional
    public CuentaResponse abrirCuenta(AperturaCuentaRequest request) {

        Cliente cliente = clienteRepository
                .findByTipoDocumentoAndNumeroDocumento(request.tipoDocumento(), request.numeroDocumento().trim())
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado"));

        if (cliente.getEstado() != EstadoCliente.ACTIVO) {
            throw new ClienteNoHabilitadoException("Cliente no habilitado");
        }

        BigDecimal saldoInicial = (request.montoApertura() == null ? BigDecimal.ZERO : request.montoApertura())
                .setScale(2, RoundingMode.HALF_UP);

        Cuenta cuenta = new Cuenta(
                cliente.getId(),
                generadorNumeroCuenta.generar(),
                request.tipoCuenta(),
                saldoInicial
        );

        return CuentaResponse.from(cuentaRepository.save(cuenta));
    }
}
