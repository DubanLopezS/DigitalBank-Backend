package com.fabricaescuela.digitalbank.auth.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del comportamiento de negocio del usuario: conteo de intentos
 * fallidos, bloqueo temporal y expiracion del bloqueo.
 */
@DisplayName("Usuario - reglas de bloqueo por intentos fallidos")
class UsuarioTest {

    @Test
    @DisplayName("El tercer intento fallido bloquea temporalmente la cuenta")
    void tercerIntentoFallidoBloqueaLaCuenta() {
        // Arrange
        Usuario usuario = nuevoUsuario();

        // Act
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();

        // Assert
        assertThat(usuario.getIntentosFallidos()).isEqualTo(3);
        assertThat(usuario.getEstadoSeguridad()).isEqualTo(EstadoSeguridad.BLOQUEADO_TEMPORAL);
        assertThat(usuario.estaBloqueado()).isTrue();
        assertThat(usuario.bloqueoExpiro()).isFalse();
    }

    @Test
    @DisplayName("El bloqueo expira una vez transcurrida la hora")
    void bloqueoExpiraDespuesDeUnaHora() {
        // Arrange
        Usuario usuario = usuarioBloqueadoHace(61);

        // Act
        boolean bloqueado = usuario.estaBloqueado();

        // Assert
        assertThat(bloqueado).isFalse();
        assertThat(usuario.bloqueoExpiro()).isTrue();
    }

    private Usuario nuevoUsuario() {
        return new Usuario(UUID.randomUUID(), "ana@banco.com", "hash", Rol.CLIENTE);
    }

    /** Deja al usuario bloqueado y desplaza la fecha de bloqueo hacia el pasado. */
    private Usuario usuarioBloqueadoHace(long minutos) {
        Usuario usuario = nuevoUsuario();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        ReflectionTestUtils.setField(usuario, "fechaBloqueo", LocalDateTime.now().minusMinutes(minutos));
        return usuario;
    }
}
