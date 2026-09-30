package com.fabricaescuela.digitalbank.auth.interfaces.repositories;

import com.fabricaescuela.digitalbank.auth.entity.Usuario;

import java.util.Optional;
import java.util.UUID;

public interface IUsuarioRepository {

    Usuario save(Usuario usuario);

    Optional<Usuario> findById(UUID id);

    boolean existsByEmail(String email);

    Optional<Usuario> findByEmail(String email);
}