package com.fabricaescuela.digitalbank.core.security;

import com.fabricaescuela.digitalbank.auth.entity.Rol;
import com.fabricaescuela.digitalbank.auth.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Se usa el JwtService real: la firma y el parseo son justamente la
 * conducta que interesa verificar.
 */
@DisplayName("JwtService - emision y lectura de tokens")
class JwtServiceTest {

    private static final String SECRETO = "secreto-de-pruebas-digital-bank-0123456789";
    private static final String OTRO_SECRETO = "otro-secreto-completamente-distinto-987654";

    private JwtService jwtService;

    @BeforeEach
    void crearServicio() {
        jwtService = new JwtService(SECRETO, 60);
    }

    @Test
    @DisplayName("Un token expirado no es valido")
    void tokenExpiradoNoEsValido() {
        // Arrange: vigencia negativa, el token nace caducado
        JwtService servicioVencido = new JwtService(SECRETO, -1);
        String tokenVencido = servicioVencido.generarToken(
                usuario(UUID.randomUUID(), UUID.randomUUID(), "ana@banco.com", Rol.CLIENTE));

        // Act
        boolean valido = jwtService.esTokenValido(tokenVencido);

        // Assert
        assertThat(valido).isFalse();
    }

    @Test
    @DisplayName("Un token manipulado en su carga no es valido")
    void tokenManipuladoNoEsValido() {
        // Arrange
        String token = jwtService.generarToken(usuario(UUID.randomUUID(), UUID.randomUUID(), "ana@banco.com", Rol.CLIENTE));
        String[] partes = token.split("\\.");
        String manipulado = partes[0] + "." + partes[1] + "X." + partes[2];

        // Act
        boolean valido = jwtService.esTokenValido(manipulado);

        // Assert
        assertThat(valido).isFalse();
    }

    private Usuario usuario(UUID usuarioId, UUID clienteId, String email, Rol rol) {
        Usuario usuario = new Usuario(clienteId, email, "hash", rol);
        ReflectionTestUtils.setField(usuario, "id", usuarioId);
        return usuario;
    }
}
