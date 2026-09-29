package com.fabricaescuela.digitalbank.auth.repository;

import com.fabricaescuela.digitalbank.auth.entity.Usuario;
import com.fabricaescuela.digitalbank.auth.interfaces.repositories.IUsuarioRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsuarioJpaRepository extends JpaRepository<Usuario, UUID>, IUsuarioRepository {
}