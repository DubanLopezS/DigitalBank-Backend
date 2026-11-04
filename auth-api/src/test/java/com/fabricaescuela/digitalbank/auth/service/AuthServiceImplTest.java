package com.fabricaescuela.digitalbank.auth.service;

import com.fabricaescuela.digitalbank.auth.dto.LoginRequest;
import com.fabricaescuela.digitalbank.auth.dto.LoginResponse;
import com.fabricaescuela.digitalbank.auth.entity.EstadoSeguridad;
import com.fabricaescuela.digitalbank.auth.entity.Rol;
import com.fabricaescuela.digitalbank.auth.entity.Usuario;
import com.fabricaescuela.digitalbank.auth.exception.CredencialesInvalidasException;
import com.fabricaescuela.digitalbank.auth.exception.CuentaBloqueadaException;
import com.fabricaescuela.digitalbank.auth.interfaces.repositories.IUsuarioRepository;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("AuthServiceImpl - autenticacion de usuarios")
class AuthServiceImplTest {

    private static final String EMAIL = "ana@banco.com";
    private static final String PASSWORD = "Password123";
    private static final String HASH = "$2a$10$hashAlmacenado";
    private static final String TOKEN = "jwt.de.prueba";

    @Mock
    private IUsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private IJwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("Credenciales correctas devuelven un token Bearer con su vigencia")
    void credencialesCorrectasDevuelvenTokenBearer() {
        // Arrange
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn(TOKEN);
        when(jwtService.getExpirationMinutes()).thenReturn(60L);

        // Act
        LoginResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD));

        // Assert
        assertThat(response.token()).isEqualTo(TOKEN);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInMinutes()).isEqualTo(60L);
    }

    @Test
    @DisplayName("Un login exitoso reinicia los intentos fallidos acumulados y persiste el usuario")
    void loginExitosoReiniciaIntentosFallidos() {
        // Arrange
        Usuario usuario = usuarioActivo();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn(TOKEN);

        // Act
        authService.login(new LoginRequest(EMAIL, PASSWORD));

        // Assert
        assertThat(usuario.getIntentosFallidos()).isZero();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @DisplayName("Un correo inexistente responde 401 sin revelar si el usuario existe")
    void correoInexistenteResponde401() {
        // Arrange
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Credenciales inválidas")
                .extracting(ex -> ((CredencialesInvalidasException) ex).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Una contrasena incorrecta responde 401, registra el intento y no emite token")
    void passwordIncorrectaRegistraIntentoFallido() {
        // Arrange
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("otra-clave", HASH)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "otra-clave")))
                .isInstanceOf(CredencialesInvalidasException.class);

        assertThat(usuario.getIntentosFallidos()).isEqualTo(1);
        assertThat(usuario.getEstadoSeguridad()).isEqualTo(EstadoSeguridad.ACTIVO);
        verify(usuarioRepository).save(usuario);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("El tercer intento fallido consecutivo deja la cuenta bloqueada temporalmente")
    void tercerIntentoFallidoBloqueaLaCuenta() {
        // Arrange
        Usuario usuario = usuarioActivo();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("otra-clave", HASH)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "otra-clave")))
                .isInstanceOf(CredencialesInvalidasException.class);

        assertThat(usuario.getEstadoSeguridad()).isEqualTo(EstadoSeguridad.BLOQUEADO_TEMPORAL);
        assertThat(usuario.estaBloqueado()).isTrue();
    }

    @Test
    @DisplayName("Una cuenta bloqueada responde 429 sin llegar a validar la contrasena")
    void cuentaBloqueadaResponde429() {
        // Arrange
        Usuario usuario = usuarioBloqueadoHace(10);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        // Act & Assert
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(CuentaBloqueadaException.class)
                .extracting(ex -> ((CuentaBloqueadaException) ex).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        verifyNoInteractions(passwordEncoder, jwtService);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Si el bloqueo ya expiro, el usuario se desbloquea y puede autenticarse")
    void bloqueoExpiradoPermiteAutenticarse() {
        // Arrange
        Usuario usuario = usuarioBloqueadoHace(61);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn(TOKEN);

        // Act
        LoginResponse response = authService.login(new LoginRequest(EMAIL, PASSWORD));

        // Assert
        assertThat(response.token()).isEqualTo(TOKEN);
        assertThat(usuario.getEstadoSeguridad()).isEqualTo(EstadoSeguridad.ACTIVO);
        assertThat(usuario.getIntentosFallidos()).isZero();
    }

    @ParameterizedTest(name = "El login con {0} encuentra al usuario")
    @ValueSource(strings = {"ANA@BANCO.COM", "Ana@Banco.Com", "aNa@bAnCo.CoM", "  ana@banco.com  "})
    @DisplayName("El login no distingue mayusculas ni espacios en el correo")
    void loginNoDistingueMayusculasEnElCorreo(String correo) {
        // Arrange
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn(TOKEN);

        // Act
        LoginResponse response = authService.login(new LoginRequest(correo, PASSWORD));

        // Assert
        assertThat(response.token()).isEqualTo(TOKEN);
        verify(usuarioRepository).findByEmail(EMAIL);
    }

    @Test
    @DisplayName("Un login exitoso registra el evento con usuarioId y rol, sin correo ni contrasena")
    void loginExitosoRegistraElEvento(CapturedOutput salida) {
        // Arrange
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn(TOKEN);

        // Act
        authService.login(new LoginRequest(EMAIL, PASSWORD));

        // Assert
        assertThat(salida.getAll())
                .contains("evento=LOGIN_EXITOSO usuarioId=" + usuario.getId() + " rol=CLIENTE")
                .doesNotContain(PASSWORD)
                .doesNotContain(EMAIL)
                .doesNotContain(HASH)
                .doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("Un login fallido con correo existente registra el usuarioId y los intentos, sin la contrasena")
    void loginFallidoRegistraElEvento(CapturedOutput salida) {
        // Arrange
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("otra-clave", HASH)).thenReturn(false);

        // Act
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "otra-clave")))
                .isInstanceOf(CredencialesInvalidasException.class);

        // Assert
        assertThat(salida.getAll())
                .contains("evento=LOGIN_FALLIDO motivo=CREDENCIALES_INVALIDAS usuarioId="
                        + usuario.getId() + " intentos=1")
                .doesNotContain("evento=USUARIO_BLOQUEADO_TEMPORAL")
                .doesNotContain("otra-clave")
                .doesNotContain(EMAIL);
    }

    @Test
    @DisplayName("Un correo no registrado deja el evento sin el correo")
    void correoNoRegistradoRegistraElEventoSinElCorreo(CapturedOutput salida) {
        // Arrange
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(CredencialesInvalidasException.class);

        // Assert
        assertThat(salida.getAll())
                .contains("evento=LOGIN_FALLIDO motivo=CORREO_NO_REGISTRADO")
                .doesNotContain(EMAIL)
                .doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("El tercer fallo registra el bloqueo temporal con el usuarioId")
    void tercerFalloRegistraElBloqueoTemporal(CapturedOutput salida) {
        // Arrange
        Usuario usuario = usuarioActivo();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("otra-clave", HASH)).thenReturn(false);

        // Act
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "otra-clave")))
                .isInstanceOf(CredencialesInvalidasException.class);

        // Assert
        assertThat(salida.getAll())
                .contains("evento=LOGIN_FALLIDO motivo=CREDENCIALES_INVALIDAS usuarioId="
                        + usuario.getId() + " intentos=3")
                .contains("evento=USUARIO_BLOQUEADO_TEMPORAL usuarioId=" + usuario.getId());
    }

    @Test
    @DisplayName("Un intento con el usuario bloqueado registra el rechazo con el usuarioId")
    void intentoConUsuarioBloqueadoRegistraElRechazo(CapturedOutput salida) {
        // Arrange
        Usuario usuario = usuarioBloqueadoHace(10);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        // Act
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(CuentaBloqueadaException.class);

        // Assert
        assertThat(salida.getAll())
                .contains("evento=LOGIN_RECHAZADO_USUARIO_BLOQUEADO usuarioId=" + usuario.getId())
                .doesNotContain(EMAIL);
    }

    private Usuario usuarioActivo() {
        Usuario usuario = new Usuario(UUID.randomUUID(), EMAIL, HASH, Rol.CLIENTE);
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        return usuario;
    }

    /** Usuario bloqueado con la fecha de bloqueo desplazada hacia el pasado. */
    private Usuario usuarioBloqueadoHace(long minutos) {
        Usuario usuario = usuarioActivo();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        usuario.registrarIntentoFallido();
        ReflectionTestUtils.setField(usuario, "fechaBloqueo", LocalDateTime.now().minusMinutes(minutos));
        return usuario;
    }
}
