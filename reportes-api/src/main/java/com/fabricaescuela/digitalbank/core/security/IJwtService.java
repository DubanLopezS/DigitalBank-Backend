package com.fabricaescuela.digitalbank.core.security;

import io.jsonwebtoken.Claims;

import java.util.UUID;

public interface IJwtService {

    Claims parseToken(String token);

    boolean esTokenValido(String token);

    UUID extraerUsuarioId(String token);

    String extraerRol(String token);

    UUID extraerClienteId(String token);
}