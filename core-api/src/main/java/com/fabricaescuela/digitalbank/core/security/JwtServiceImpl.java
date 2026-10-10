package com.fabricaescuela.digitalbank.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtServiceImpl implements IJwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtServiceImpl(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    @Override
    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    @Override
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public boolean esTokenValido(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public UUID extraerUsuarioId(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    @Override
    public String extraerRol(String token) {
        return parseToken(token).get("rol", String.class);
    }

    @Override
    public UUID extraerClienteId(String token) {
        String clienteId = parseToken(token).get("clienteId", String.class);
        return clienteId != null ? UUID.fromString(clienteId) : null;
    }
}