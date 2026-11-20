package com.fabricaescuela.digitalbank.cliente.service;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteDetalleResponse;
import com.fabricaescuela.digitalbank.cliente.dto.CuentaDelClienteResponse;
import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cliente.exception.ParametroInvalidoException;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.IClienteRepository;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.ICuentasDelClienteRepository;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IConsultaClienteService;
import com.fabricaescuela.digitalbank.core.exception.AccesoNoAutorizadoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConsultaClienteServiceImpl implements IConsultaClienteService {

    private static final Logger log = LoggerFactory.getLogger(ConsultaClienteServiceImpl.class);
    private static final int LONGITUD_MAXIMA_DOCUMENTO = 30;
    private static final String ROL_CLIENTE = "CLIENTE";

    private final IClienteRepository clienteRepository;
    private final ICuentasDelClienteRepository cuentasRepository;

    public ConsultaClienteServiceImpl(IClienteRepository clienteRepository,
                                    ICuentasDelClienteRepository cuentasRepository) {
        this.clienteRepository = clienteRepository;
        this.cuentasRepository = cuentasRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteDetalleResponse consultar(TipoDocumento tipoDocumento, String numeroDocumento,
                                            UUID clienteIdAutenticado, String rol) {
        String documento = validarDocumento(numeroDocumento);

        Optional<Cliente> encontrado =
                clienteRepository.findByTipoDocumentoAndNumeroDocumento(tipoDocumento, documento);

        Cliente cliente = ROL_CLIENTE.equals(rol)
                ? resolverParaCliente(encontrado, clienteIdAutenticado)
                : encontrado.orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado"));

        List<CuentaDelClienteResponse> cuentas = cuentasRepository.findByClienteId(cliente.getId());
        log.info("evento=CLIENTE_CONSULTADO clienteId={} cuentas={}", cliente.getId(), cuentas.size());
        return ClienteDetalleResponse.from(cliente, cuentas);
    }

    // ADR-0005: a un CLIENTE, el documento ajeno o inexistente le da el mismo 403.
    private Cliente resolverParaCliente(Optional<Cliente> encontrado, UUID clienteIdAutenticado) {
        return encontrado
                .filter(cliente -> clienteIdAutenticado != null && cliente.getId().equals(clienteIdAutenticado))
                .orElseThrow(AccesoNoAutorizadoException::new);
    }

    private String validarDocumento(String numeroDocumento) {
        String documento = numeroDocumento == null ? "" : numeroDocumento.trim();
        if (documento.isEmpty() || documento.length() > LONGITUD_MAXIMA_DOCUMENTO) {
            throw new ParametroInvalidoException("Parámetro inválido: numeroDocumento");
        }
        return documento;
    }
}