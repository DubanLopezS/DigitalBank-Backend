package com.fabricaescuela.digitalbank.core.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Errores 401 y 403 de seguridad")
class SecurityErrorHandlersTest {

    private final ErrorResponseWriter writer = new ErrorResponseWriter();
    private final ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(writer);
    private final ApiAccessDeniedHandler deniedHandler = new ApiAccessDeniedHandler(writer);

    private MockHttpServletRequest peticion(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    @Test
    @DisplayName("El 401 trae errorCode, mensaje con tilde y traceId")
    void respuesta401() throws Exception {
        // Arrange
        MDC.put(TraceIdFilter.MDC_KEY, "abc-12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            // Act
            entryPoint.commence(peticion("/api/v1/cuentas"), response, new BadCredentialsException("x"));
        } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
        }

        // Assert
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"errorCode\":\"NO_AUTENTICADO\"")
                .contains("\"message\":\"Token no proporcionado o inválido\"")
                .contains("\"path\":\"/api/v1/cuentas\"")
                .contains("\"traceId\":\"abc-12345678\"");
    }

    @Test
    @DisplayName("El 403 trae errorCode y mensaje de acceso no autorizado")
    void respuesta403() throws Exception {
        // Arrange
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        deniedHandler.handle(peticion("/api/v1/clientes"), response, new AccessDeniedException("x"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"errorCode\":\"ACCESO_NO_AUTORIZADO\"")
                .contains("\"message\":\"Acceso no autorizado\"")
                .contains("\"traceId\":null");
    }

    @Test
    @DisplayName("Una ruta con comillas no rompe el JSON")
    void rutaConComillasSeEscapa() throws Exception {
        // Arrange
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        entryPoint.commence(peticion("/api/\"raro"), response, new BadCredentialsException("x"));

        // Assert
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"path\":\"/api/\\\"raro\"");
    }
}