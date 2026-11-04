package com.fabricaescuela.digitalbank.auth.service;

import com.fabricaescuela.digitalbank.auth.dto.LoginRequest;
import com.fabricaescuela.digitalbank.auth.dto.LoginResponse;
import com.fabricaescuela.digitalbank.auth.entity.EstadoSeguridad;
import com.fabricaescuela.digitalbank.auth.entity.Usuario;
import com.fabricaescuela.digitalbank.auth.exception.CredencialesInvalidasException;
import com.fabricaescuela.digitalbank.auth.exception.CuentaBloqueadaException;
import com.fabricaescuela.digitalbank.auth.interfaces.repositories.IUsuarioRepository;
import com.fabricaescuela.digitalbank.auth.interfaces.services.IAuthService;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.util.NormalizadorCorreo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements IAuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final IUsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final IJwtService jwtService;

    public AuthServiceImpl(IUsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, 
                            IJwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }


    @Override
    @Transactional(noRollbackFor = CredencialesInvalidasException.class)
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(NormalizadorCorreo.normalizar(request.email()))
                .orElseThrow(() -> {
                    log.warn("evento=LOGIN_FALLIDO motivo=CORREO_NO_REGISTRADO");
                    return new CredencialesInvalidasException();
                });

        if (usuario.bloqueoExpiro()) {
            usuario.desbloquear();
        }
        if (usuario.estaBloqueado()) {
            log.warn("evento=LOGIN_RECHAZADO_USUARIO_BLOQUEADO usuarioId={}", usuario.getId());
            throw new CuentaBloqueadaException();
        }

        boolean passwordCorrecta = passwordEncoder.matches(request.password(), usuario.getPasswordHash());

        if (!passwordCorrecta) {
            usuario.registrarIntentoFallido();
            usuarioRepository.save(usuario);
            log.warn("evento=LOGIN_FALLIDO motivo=CREDENCIALES_INVALIDAS usuarioId={} intentos={}",
                    usuario.getId(), usuario.getIntentosFallidos());
            if (usuario.getEstadoSeguridad() == EstadoSeguridad.BLOQUEADO_TEMPORAL) {
                log.warn("evento=USUARIO_BLOQUEADO_TEMPORAL usuarioId={}", usuario.getId());
            }
            throw new CredencialesInvalidasException();
        }

        usuario.registrarLoginExitoso();
        usuarioRepository.save(usuario);

        String token = jwtService.generarToken(usuario);
        LoginResponse response = LoginResponse.of(token, jwtService.getExpirationMinutes());
        log.info("evento=LOGIN_EXITOSO usuarioId={} rol={}", usuario.getId(), usuario.getRol());
        return response;
    }
}
