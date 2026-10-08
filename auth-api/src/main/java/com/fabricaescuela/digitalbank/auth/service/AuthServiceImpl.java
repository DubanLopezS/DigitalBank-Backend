package com.fabricaescuela.digitalbank.auth.service;

import com.fabricaescuela.digitalbank.auth.dto.LoginRequest;
import com.fabricaescuela.digitalbank.auth.dto.LoginResponse;
import com.fabricaescuela.digitalbank.auth.entity.Usuario;
import com.fabricaescuela.digitalbank.auth.exception.CredencialesInvalidasException;
import com.fabricaescuela.digitalbank.auth.exception.CuentaBloqueadaException;
import com.fabricaescuela.digitalbank.auth.interfaces.repositories.IUsuarioRepository;
import com.fabricaescuela.digitalbank.auth.interfaces.services.IAuthService;
import com.fabricaescuela.digitalbank.core.security.IJwtService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements IAuthService {

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
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(CredencialesInvalidasException::new);

        if (usuario.bloqueoExpiro()) {
            usuario.desbloquear();
        }
        if (usuario.estaBloqueado()) {
            throw new CuentaBloqueadaException();
        }

        boolean passwordCorrecta = passwordEncoder.matches(request.password(), usuario.getPasswordHash());

        if (!passwordCorrecta) {
            usuario.registrarIntentoFallido();
            usuarioRepository.save(usuario);
            throw new CredencialesInvalidasException();
        }

        usuario.registrarLoginExitoso();
        usuarioRepository.save(usuario);

        String token = jwtService.generarToken(usuario);
        return LoginResponse.of(token, jwtService.getExpirationMinutes());
    }
}
