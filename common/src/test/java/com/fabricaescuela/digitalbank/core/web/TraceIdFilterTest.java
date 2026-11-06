package com.fabricaescuela.digitalbank.core.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TraceIdFilter - id de seguimiento por peticion")
class TraceIdFilterTest {

    private final TraceIdFilter filtro = new TraceIdFilter();

    @Test
    @DisplayName("Sin header genera un UUID y lo devuelve en la respuesta")
    void sinHeaderGeneraUnUuid() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filtro.doFilter(request, response, new MockFilterChain());

        // Assert
        String traceId = response.getHeader(TraceIdFilter.HEADER);
        assertThat(traceId).isNotBlank();
        assertThat(java.util.UUID.fromString(traceId)).isNotNull();
    }

    @Test
    @DisplayName("Un header valido se respeta")
    void headerValidoSeRespeta() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "abc-12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filtro.doFilter(request, response, new MockFilterChain());

        // Assert
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo("abc-12345678");
    }

    @Test
    @DisplayName("Un header con caracteres peligrosos se descarta y se genera otro")
    void headerInvalidoSeDescarta() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "malo\nlinea-falsa");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filtro.doFilter(request, response, new MockFilterChain());

        // Assert
        assertThat(response.getHeader(TraceIdFilter.HEADER))
                .isNotEqualTo("malo\nlinea-falsa")
                .doesNotContain("\n");
    }

    @Test
    @DisplayName("El traceId esta en el MDC durante la peticion y se limpia al terminar")
    void mdcSeLimpiaAlTerminar() throws Exception {
        // Arrange
        AtomicReference<String> durante = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "abc-12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filtro.doFilter(request, response, (req, res) -> durante.set(MDC.get(TraceIdFilter.MDC_KEY)));

        // Assert
        assertThat(durante.get()).isEqualTo("abc-12345678");
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }
}