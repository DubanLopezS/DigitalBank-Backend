package com.fabricaescuela.digitalbank.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtServiceImpl - validacion de tokens (solo lectura)")
class JwtServiceImplTest {

    private static final String SECRETO = "secreto-de-pruebas-reportes-api-0123456789";
    private static final String OTRO_SECRETO = "otro-secreto-distinto-para-pruebas-9876543210";

    private final JwtServiceImpl jwtService = new JwtServiceImpl(SECRETO);

    @Test
    @DisplayName("Un token firmado con el mismo secreto es valido")
    void tokenConElMismoSecretoEsValido() {
        String token = crearToken(SECRETO, UUID.randomUUID(), "ADMIN", null, enUnaHora());

        assertThat(jwtService.esTokenValido(token)).isTrue();
    }

    @Test
    @DisplayName("Un token firmado con otro secreto no es valido")
    void tokenConOtroSecretoNoEsValido() {
        String token = crearToken(OTRO_SECRETO, UUID.randomUUID(), "ADMIN", null, enUnaHora());

        assertThat(jwtService.esTokenValido(token)).isFalse();
    }

    @Test
    @DisplayName("Un token vencido no es valido")
    void tokenVencidoNoEsValido() {
        String token = crearToken(SECRETO, UUID.randomUUID(), "ADMIN", null,
                new Date(System.currentTimeMillis() - 60_000));

        assertThat(jwtService.esTokenValido(token)).isFalse();
    }

    @Test
    @DisplayName("Un texto cualquiera no es un token valido")
    void textoCualquieraNoEsValido() {
        assertThat(jwtService.esTokenValido("esto-no-es-un-token")).isFalse();
    }

    @Test
    @DisplayName("Un token vacio no es valido")
    void tokenVacioNoEsValido() {
        assertThat(jwtService.esTokenValido("")).isFalse();
    }

    @Test
    @DisplayName("Se extraen el usuario, el rol y el cliente del token")
    void seExtraenLosDatosDelToken() {
        UUID usuarioId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        String token = crearToken(SECRETO, usuarioId, "CLIENTE", clienteId, enUnaHora());

        assertThat(jwtService.extraerUsuarioId(token)).isEqualTo(usuarioId);
        assertThat(jwtService.extraerRol(token)).isEqualTo("CLIENTE");
        assertThat(jwtService.extraerClienteId(token)).isEqualTo(clienteId);
    }

    @Test
    @DisplayName("Un token sin clienteId (ADMIN o CAJERO) devuelve cliente nulo")
    void tokenSinClienteIdDevuelveNulo() {
        String token = crearToken(SECRETO, UUID.randomUUID(), "ADMIN", null, enUnaHora());

        assertThat(jwtService.extraerClienteId(token)).isNull();
    }

    @Test
    @DisplayName("parseToken devuelve los claims de un token valido")
    void parseTokenDevuelveLosClaims() {
        UUID usuarioId = UUID.randomUUID();
        String token = crearToken(SECRETO, usuarioId, "CAJERO", null, enUnaHora());

        Claims claims = jwtService.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo(usuarioId.toString());
    }

    @Test
    @DisplayName("parseToken lanza excepcion con un token firmado con otro secreto")
    void parseTokenFallaConOtroSecreto() {
        String token = crearToken(OTRO_SECRETO, UUID.randomUUID(), "ADMIN", null, enUnaHora());

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    private Date enUnaHora() {
        return new Date(System.currentTimeMillis() + 3_600_000);
    }

    private String crearToken(String secreto, UUID usuarioId, String rol, UUID clienteId, Date expira) {
        SecretKey clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        var builder = Jwts.builder()
                .subject(usuarioId.toString())
                .claim("rol", rol)
                .expiration(expira);
        if (clienteId != null) {
            builder.claim("clienteId", clienteId.toString());
        }
        return builder.signWith(clave).compact();
    }
}