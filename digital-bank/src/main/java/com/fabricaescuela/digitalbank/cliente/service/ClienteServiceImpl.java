package com.fabricaescuela.digitalbank.cliente.service;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteRegistroRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResponse;
import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteMenorDeEdadException;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteYaExisteException;
import com.fabricaescuela.digitalbank.cliente.repository.ClienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fabricaescuela.digitalbank.cliente.interfaces.ClienteService;
import com.fabricaescuela.digitalbank.auth.interfaces.CredencialesService;

import java.time.LocalDate;
import java.time.Period;

@Service
public class ClienteServiceImpl implements ClienteService {

    private static final int EDAD_MINIMA = 18;

    private final ClienteRepository clienteRepository;
    private final CredencialesService credencialesService;

    public ClienteServiceImpl(ClienteRepository clienteRepository, CredencialesService credencialesService) {
        this.clienteRepository = clienteRepository;
        this.credencialesService = credencialesService;
    }

    @Override
    @Transactional
    public ClienteResponse registrarCliente(ClienteRegistroRequest request) {
        validarDocumentoUnico(request.tipoDocumento(), request.numeroDocumento());
        validarEmailUnico(request.email());
        validarMayoriaDeEdad(request.fechaNacimiento());

        Cliente cliente = new Cliente(
                request.tipoDocumento(),
                request.numeroDocumento(),
                request.nombres(),
                request.apellidos(),
                request.fechaNacimiento(),
                request.telefono(),
                request.email()
        );
        Cliente clienteGuardado = clienteRepository.save(cliente);

        credencialesService.registrarCredencialesCliente(
                clienteGuardado.getId(),
                clienteGuardado.getEmail(),
                request.password()
        );

        return ClienteResponse.from(clienteGuardado);
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