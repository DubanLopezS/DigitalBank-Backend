package com.fabricaescuela.digitalbank.auth.service;

import com.fabricaescuela.digitalbank.auth.entity.Rol;
import com.fabricaescuela.digitalbank.auth.entity.Usuario;
import com.fabricaescuela.digitalbank.auth.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.fabricaescuela.digitalbank.auth.interfaces.CredencialesService;

import java.util.UUID;

@Service
public class CredencialesServiceImpl implements CredencialesService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public CredencialesServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void registrarCredencialesCliente(UUID clienteId, String email, String rawPassword) {
        String hash = passwordEncoder.encode(rawPassword); // Encriptar la contraseña antes de guardarla
        Usuario usuario = new Usuario(clienteId, email, hash, Rol.CLIENTE);
        usuarioRepository.save(usuario);
    }
}
