package com.fabricaescuela.digitalbank.auth.service;

import com.fabricaescuela.digitalbank.auth.entity.Usuario;
import com.fabricaescuela.digitalbank.auth.interfaces.UsuarioQueryService;
import com.fabricaescuela.digitalbank.auth.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UsuarioQueryServiceImpl implements UsuarioQueryService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioQueryServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> obtenerClienteId(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(Usuario::getClienteId);
    }
}
