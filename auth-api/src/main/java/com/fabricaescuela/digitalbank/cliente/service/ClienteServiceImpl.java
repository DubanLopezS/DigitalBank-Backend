package com.fabricaescuela.digitalbank.cliente.service;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteRegistroRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResponse;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResumenResponse;
import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteMenorDeEdadException;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteYaExisteException;
import com.fabricaescuela.digitalbank.core.util.NormalizadorCorreo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.IClienteRepository;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteService;
import com.fabricaescuela.digitalbank.auth.interfaces.services.ICredencialesService;

import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

@Service
public class ClienteServiceImpl implements IClienteService {

    private static final Logger log = LoggerFactory.getLogger(ClienteServiceImpl.class);
    private static final int EDAD_MINIMA = 18;

    private final IClienteRepository clienteRepository;
    private final ICredencialesService credencialesService;

    public ClienteServiceImpl(IClienteRepository clienteRepository, ICredencialesService credencialesService) {
        this.clienteRepository = clienteRepository;
        this.credencialesService = credencialesService;
    }

    @Override
    @Transactional
    public ClienteResponse registrarCliente(ClienteRegistroRequest request) {
        String email = NormalizadorCorreo.normalizar(request.email());

        validarDocumentoUnico(request.tipoDocumento(), request.numeroDocumento());
        validarEmailUnico(email);
        validarMayoriaDeEdad(request.fechaNacimiento());

        Cliente cliente = new Cliente(
                request.tipoDocumento(),
                request.numeroDocumento(),
                request.nombres(),
                request.apellidos(),
                request.fechaNacimiento(),
                request.telefono(),
                email
        );
        Cliente clienteGuardado = clienteRepository.save(cliente);

        credencialesService.registrarCredencialesCliente(
                clienteGuardado.getId(),
                clienteGuardado.getEmail(),
                request.password()
        );

        log.info("evento=CLIENTE_REGISTRADO clienteId={}", clienteGuardado.getId());
        return ClienteResponse.from(clienteGuardado);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClienteResumenResponse> obtenerResumenPorDocumento(TipoDocumento tipoDocumento, String numeroDocumento) {
        return clienteRepository.findByTipoDocumentoAndNumeroDocumento(tipoDocumento, numeroDocumento)
                .map(ClienteResumenResponse::from);
    }

    private void validarDocumentoUnico(TipoDocumento tipoDocumento, String numeroDocumento) {
        if (clienteRepository.existsByTipoDocumentoAndNumeroDocumento(tipoDocumento, numeroDocumento)) {
            throw new ClienteYaExisteException("El cliente ya existe");
        }
    }

    private void validarEmailUnico(String email) {
        if (clienteRepository.existsByEmail(email)) {
            throw new ClienteYaExisteException("El cliente ya existe");
        }
    }

    private void validarMayoriaDeEdad(LocalDate fechaNacimiento) {
        int edad = Period.between(fechaNacimiento, LocalDate.now()).getYears();
        if (edad < EDAD_MINIMA) {
            throw new ClienteMenorDeEdadException("El cliente debe ser mayor de edad");
        }
    }
}