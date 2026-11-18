package com.fabricaescuela.digitalbank.core.config;

import com.fabricaescuela.digitalbank.cliente.controller.ClienteController;
import com.fabricaescuela.digitalbank.cliente.controller.ConsultaClienteController;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IClienteService;
import com.fabricaescuela.digitalbank.cliente.interfaces.services.IConsultaClienteService;
import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
import com.fabricaescuela.digitalbank.core.web.ApiAccessDeniedHandler;
import com.fabricaescuela.digitalbank.core.web.ApiAuthenticationEntryPoint;
import com.fabricaescuela.digitalbank.core.web.ErrorResponseWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ClienteController.class, ConsultaClienteController.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ErrorResponseWriter.class})
@DisplayName("SecurityConfig - rutas protegidas de auth-api sin token")
class RutasProtegidasSinTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private IClienteService clienteService;

    @MockitoBean
    private IConsultaClienteService consultaClienteService;

    static Stream<Arguments> rutasProtegidas() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/clientes", null),
                Arguments.of("POST", "/api/v1/clientes", "{}"),
                Arguments.of("PATCH", "/api/v1/clientes/" + UUID.randomUUID(), "{\"telefono\":\"3001234567\"}")
        );
    }

    @ParameterizedTest(name = "{0} {1} sin token responde 401")
    @MethodSource("rutasProtegidas")
    @DisplayName("Cada ruta protegida responde 401 sin token y no invoca ningun servicio")
    void rutaProtegidaSinTokenResponde401(String metodo, String ruta, String body) throws Exception {
        var peticion = request(HttpMethod.valueOf(metodo), ruta);
        if (body != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        mockMvc.perform(peticion)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(ruta));
        verifyNoInteractions(clienteService, consultaClienteService);
    }
}