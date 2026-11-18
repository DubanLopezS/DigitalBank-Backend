package com.fabricaescuela.digitalbank.cliente.service;

import com.fabricaescuela.digitalbank.auth.interfaces.repositories.IUsuarioRepository;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteActualizacionRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteRegistroRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResponse;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResumenResponse;
import com.fabricaescuela.digitalbank.cliente.entity.AuditoriaDatosCliente;
import com.fabricaescuela.digitalbank.cliente.entity.CampoAuditado;
import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteMenorDeEdadException;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteNoEncontradoException;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteYaExisteException;
import com.fabricaescuela.digitalbank.cliente.exception.CorreoYaRegistradoException;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.IAuditoriaDatosClienteRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClienteServiceImpl implements IClienteService {

    private static final Logger log = LoggerFactory.getLogger(ClienteServiceImpl.class);
    private static final int EDAD_MINIMA = 18;

    private final IClienteRepository clienteRepository;
    private final ICredencialesService credencialesService;
    private final IUsuarioRepository usuarioRepository;
    private final IAuditoriaDatosClienteRepository auditoriaRepository;

    public ClienteServiceImpl(IClienteRepository clienteRepository, ICredencialesService credencialesService,
                              IUsuarioRepository usuarioRepository,
                              IAuditoriaDatosClienteRepository auditoriaRepository) {
        this.clienteRepository = clienteRepository;
        this.credencialesService = credencialesService;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaRepository = auditoriaRepository;
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
    @Transactional
    public ClienteResponse actualizarCliente(UUID clienteId, ClienteActualizacionRequest request, UUID usuarioId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ClienteNoEncontradoException("Cliente no encontrado"));

        List<AuditoriaDatosCliente> cambios = new ArrayList<>();

        String nuevoEmail = NormalizadorCorreo.normalizar(request.email());
        if (nuevoEmail != null && !nuevoEmail.equals(cliente.getEmail())) {
            validarCorreoDisponible(nuevoEmail, clienteId);
            cambios.add(new AuditoriaDatosCliente(
                    clienteId, usuarioId, CampoAuditado.EMAIL, cliente.getEmail(), nuevoEmail));
            cliente.cambiarEmail(nuevoEmail);
            actualizarCorreoDelUsuario(clienteId, nuevoEmail);
        }

        String nuevoTelefono = request.telefono();
        if (nuevoTelefono != null && !nuevoTelefono.equals(cliente.getTelefono())) {
            // valor_anterior es NOT NULL en la V6 y un cliente antiguo puede no tener teléfono.
            String telefonoAnterior = cliente.getTelefono() == null ? "" : cliente.getTelefono();
            cambios.add(new AuditoriaDatosCliente(
                    clienteId, usuarioId, CampoAuditado.TELEFONO, telefonoAnterior, nuevoTelefono));
            cliente.cambiarTelefono(nuevoTelefono);
        }

        if (cambios.isEmpty()) {
            return ClienteResponse.from(cliente);
        }

        Cliente clienteGuardado = clienteRepository.save(cliente);
        cambios.forEach(auditoriaRepository::save);

        String camposCambiados = cambios.stream()
                .map(cambio -> cambio.getCampo().name())
                .collect(Collectors.joining(","));
        log.info("evento=CLIENTE_ACTUALIZADO clienteId={} usuarioId={} campos={}",
                clienteId, usuarioId, camposCambiados);
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

    // El correo no puede ser de otro cliente ni de otro usuario (por ejemplo un ADMIN o un CAJERO),
    // porque uk_cliente_email y uk_usuario_email harían fallar el guardado.
    private void validarCorreoDisponible(String email, UUID clienteId) {
        boolean usadoPorOtroUsuario = usuarioRepository.findByEmail(email)
                .filter(usuario -> !clienteId.equals(usuario.getClienteId()))
                .isPresent();
        if (clienteRepository.existsByEmail(email) || usadoPorOtroUsuario) {
            throw new CorreoYaRegistradoException("El correo ya está registrado");
        }
    }

    private void actualizarCorreoDelUsuario(UUID clienteId, String nuevoEmail) {
        usuarioRepository.findByClienteId(clienteId).ifPresent(usuario -> {
            usuario.cambiarEmail(nuevoEmail);
            usuarioRepository.save(usuario);
        });
    }

    private void validarMayoriaDeEdad(LocalDate fechaNacimiento) {
        int edad = Period.between(fechaNacimiento, LocalDate.now()).getYears();
        if (edad < EDAD_MINIMA) {
            throw new ClienteMenorDeEdadException("El cliente debe ser mayor de edad");
        }
    }
}