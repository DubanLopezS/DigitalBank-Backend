package com.fabricaescuela.digitalbank.core.security;

import com.fabricaescuela.digitalbank.auth.entity.Usuario;
import io.jsonwebtoken.Claims;

import java.util.UUID;

public interface IJwtService {

    String generarToken(Usuario usuario);

    long getExpirationMinutes();

    Claims parseToken(String token);

    boolean esTokenValido(String token);

    UUID extraerUsuarioId(String token);

    String extraerRol(String token);

    UUID extraerClienteId(String token);
}
