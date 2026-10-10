package com.fabricaescuela.digitalbank.auth.service;

import com.fabricaescuela.digitalbank.auth.entity.Usuario;
import com.fabricaescuela.digitalbank.auth.interfaces.repositories.IUsuarioRepository;
import com.fabricaescuela.digitalbank.auth.interfaces.services.IUsuarioQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UsuarioQueryServiceImpl implements IUsuarioQueryService {

    private final IUsuarioRepository usuarioRepository;

    public UsuarioQueryServiceImpl(IUsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> obtenerClienteId(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .map(Usuario::getClienteId);
    }
}
