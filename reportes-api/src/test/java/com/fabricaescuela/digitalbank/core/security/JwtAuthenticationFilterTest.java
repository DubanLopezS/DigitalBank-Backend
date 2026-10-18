package com.fabricaescuela.digitalbank.core.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter - autenticacion a partir del token")
class JwtAuthenticationFilterTest {

    @Mock
    private IJwtService jwtService;

    @InjectMocks
    private JwtAuthenticationFilter filtro;

    @AfterEach
    void limpiarContexto() {
        // El contexto de seguridad es global: se limpia para no afectar otros tests
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Sin header Authorization no autentica y la peticion continua")
    void sinHeaderNoAutentica() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(request, new MockHttpServletResponse(), cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadena.getRequest()).isNotNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Un header que no empieza con Bearer se ignora")
    void headerSinBearerSeIgnora() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc123");
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(request, new MockHttpServletResponse(), cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadena.getRequest()).isNotNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Un token invalido no autentica y la peticion continua")
    void tokenInvalidoNoAutentica() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-malo");
        MockFilterChain cadena = new MockFilterChain();
        when(jwtService.esTokenValido("token-malo")).thenReturn(false);

        filtro.doFilter(request, new MockHttpServletResponse(), cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Un token valido deja el usuario, el rol y el cliente en el contexto")
    void tokenValidoAutentica() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-bueno");
        MockFilterChain cadena = new MockFilterChain();
        when(jwtService.esTokenValido("token-bueno")).thenReturn(true);
        when(jwtService.extraerUsuarioId("token-bueno")).thenReturn(usuarioId);
        when(jwtService.extraerRol("token-bueno")).thenReturn("CLIENTE");
        when(jwtService.extraerClienteId("token-bueno")).thenReturn(clienteId);

        filtro.doFilter(request, new MockHttpServletResponse(), cadena);

        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        assertThat(autenticacion).isNotNull();
        assertThat(autenticacion.getPrincipal()).isEqualTo(usuarioId);
        assertThat(autenticacion.getDetails()).isEqualTo(clienteId);
        assertThat(autenticacion.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_CLIENTE");
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Un ADMIN no tiene clienteId: los detalles quedan nulos")
    void adminNoTieneClienteId() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-admin");
        when(jwtService.esTokenValido("token-admin")).thenReturn(true);
        when(jwtService.extraerUsuarioId("token-admin")).thenReturn(usuarioId);
        when(jwtService.extraerRol("token-admin")).thenReturn("ADMIN");
        when(jwtService.extraerClienteId("token-admin")).thenReturn(null);

        filtro.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        assertThat(autenticacion.getDetails()).isNull();
        assertThat(autenticacion.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }
}