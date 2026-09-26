package com.fabricaescuela.digitalbank.auth.service;

import com.fabricaescuela.digitalbank.auth.dto.LoginRequest;
import com.fabricaescuela.digitalbank.auth.dto.LoginResponse;
import com.fabricaescuela.digitalbank.auth.entity.EstadoSeguridad;
import com.fabricaescuela.digitalbank.auth.entity.Rol;
import com.fabricaescuela.digitalbank.auth.entity.Usuario;
import com.fabricaescuela.digitalbank.auth.exception.CredencialesInvalidasException;
import com.fabricaescuela.digitalbank.auth.exception.CuentaBloqueadaException;
import com.fabricaescuela.digitalbank.auth.repository.UsuarioRepository;
import com.fabricaescuela.digitalbank.core.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final UUID CLIENTE_ID = UUID.randomUUID();
    private static final String EMAIL = "cliente@example.com";
    private static final String PASSWORD_HASH = "hash-simulado";
    private static final String RAW_PASSWORD = "Clave1234";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("Con credenciales validas se retorna un token y se registra el login exitoso")
    void login_credencialesValidas_debePermitirAcceso() {
        Usuario usuario = nuevoUsuario();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn("token-valido");
        when(jwtService.getExpirationMinutes()).thenReturn(60L);

        LoginResponse response = authService.login(new LoginRequest(EMAIL, RAW_PASSWORD));

        assertEquals("token-valido", response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals(60L, response.expiresInMinutes());
        assertEquals(EstadoSeguridad.ACTIVO, usuario.getEstadoSeguridad());
        assertEquals(0, usuario.getIntentosFallidos());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @DisplayName("Contrasena incorrecta responde con mensaje generico, sin revelar cual dato fallo")
    void login_contrasenaIncorrecta_debeRechazarSinRevelarCualDatoFallo() {
        Usuario usuario = nuevoUsuario();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);

        CredencialesInvalidasException ex = assertThrows(
                CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD))
        );

        assertEquals("Credenciales inválidas", ex.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals(1, usuario.getIntentosFallidos());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @DisplayName("Correo inexistente responde con el mismo mensaje generico que una contrasena incorrecta")
    void login_correoInexistente_debeRechazarConMensajeGenerico() {
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        CredencialesInvalidasException ex = assertThrows(
                CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD))
        );

        assertEquals("Credenciales inválidas", ex.getMessage());
        verifyNoInteractions(passwordEncoder);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Al tercer intento fallido la cuenta queda bloqueada temporalmente")
    void login_tercerIntentoFallido_debeBloquearCuenta() {
        Usuario usuario = nuevoUsuario();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);

        assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)));

        assertEquals(3, usuario.getIntentosFallidos());
        assertEquals(EstadoSeguridad.BLOQUEADO_TEMPORAL, usuario.getEstadoSeguridad());
        assertTrue(usuario.estaBloqueado());
    }

    @Test
    @DisplayName("Cuenta bloqueada rechaza el acceso incluso con credenciales correctas")
    void login_cuentaBloqueada_debeRechazarInclusoConCredencialesCorrectas() {
        Usuario usuario = nuevoUsuario();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        CuentaBloqueadaException ex = assertThrows(
                CuentaBloqueadaException.class,
                () -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD))
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        verifyNoInteractions(passwordEncoder);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Pasada la hora de bloqueo, el usuario se desbloquea automaticamente y puede acceder de nuevo")
    void login_bloqueoExpirado_debePermitirAccesoNuevamente() throws Exception {
        Usuario usuario = nuevoUsuario();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        fijarFechaBloqueoEnElPasado(usuario, LocalDateTime.now().minusHours(2));
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn("token-valido");
        when(jwtService.getExpirationMinutes()).thenReturn(60L);

        LoginResponse response = authService.login(new LoginRequest(EMAIL, RAW_PASSWORD));

        assertEquals("token-valido", response.token());
        assertEquals(EstadoSeguridad.ACTIVO, usuario.getEstadoSeguridad());
        assertEquals(0, usuario.getIntentosFallidos());
    }

    @Test
    @DisplayName("Un login exitoso antes del tercer intento reinicia el conteo de fallos")
    void login_exitosoAntesDelTercerIntento_debeReiniciarConteo() {
        Usuario usuario = nuevoUsuario();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn("token-valido");
        when(jwtService.getExpirationMinutes()).thenReturn(60L);

        authService.login(new LoginRequest(EMAIL, RAW_PASSWORD));

        assertEquals(0, usuario.getIntentosFallidos());
        assertEquals(EstadoSeguridad.ACTIVO, usuario.getEstadoSeguridad());
    }

    private Usuario nuevoUsuario() {
        return new Usuario(CLIENTE_ID, EMAIL, PASSWORD_HASH, Rol.CLIENTE);
    }

    /**
     * Usuario no expone un setter para fechaBloqueo (solo se fija internamente en
     * registrarIntentoFallido()); se usa reflection para simular que el bloqueo
     * ocurrio hace mas de 1 hora y asi probar bloqueoExpiro()/estaBloqueado() reales.
     */
    private void fijarFechaBloqueoEnElPasado(Usuario usuario, LocalDateTime fecha) throws Exception {
        Field field = Usuario.class.getDeclaredField("fechaBloqueo");
        field.setAccessible(true);
        field.set(usuario, fecha);
    }
}
