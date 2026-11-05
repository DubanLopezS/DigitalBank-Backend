package com.fabricaescuela.digitalbank.core.config;

import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
import com.fabricaescuela.digitalbank.core.web.ApiAccessDeniedHandler;
import com.fabricaescuela.digitalbank.core.web.ApiAuthenticationEntryPoint;
import com.fabricaescuela.digitalbank.core.web.ErrorResponseWriter;
import com.fabricaescuela.digitalbank.cuenta.controller.CuentaController;
import com.fabricaescuela.digitalbank.cuenta.controller.EstadoCuentaController;
import com.fabricaescuela.digitalbank.cuenta.controller.SaldoController;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ICuentaService;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.IEstadoCuentaService;
import com.fabricaescuela.digitalbank.cuenta.interfaces.services.ISaldoService;
import com.fabricaescuela.digitalbank.transaccion.controller.RetiroController;
import com.fabricaescuela.digitalbank.transaccion.controller.TransferenciaController;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IRetiroService;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.ITransferenciaService;
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

@WebMvcTest({CuentaController.class, SaldoController.class, RetiroController.class,
        TransferenciaController.class, EstadoCuentaController.class})
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ErrorResponseWriter.class})
@DisplayName("SecurityConfig - rutas protegidas de core-api sin token")
class RutasProtegidasSinTokenTest {

    private static final UUID CUENTA_ID = UUID.randomUUID();
    private static final String MOTIVO = "{\"motivo\":\"Actividad sospechosa detectada\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private ICuentaService cuentaService;

    @MockitoBean
    private ISaldoService saldoService;

    @MockitoBean
    private IRetiroService retiroService;

    @MockitoBean
    private ITransferenciaService transferenciaService;

    @MockitoBean
    private IEstadoCuentaService estadoCuentaService;

    static Stream<Arguments> rutasProtegidas() {
        return Stream.of(
                Arguments.of("POST", "/api/v1/cuentas",
                        "{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"1020304050\",\"tipoCuenta\":\"AHORROS\"}"),
                Arguments.of("GET", "/api/v1/cuentas/1234567890/saldo", null),
                Arguments.of("POST", "/api/v1/cuentas/" + CUENTA_ID + "/retiros", "{\"monto\":1000.00}"),
                Arguments.of("POST", "/api/v1/cuentas/" + CUENTA_ID + "/transferencias",
                        "{\"cuentaDestino\":\"1234567890\",\"monto\":1000.00}"),
                Arguments.of("POST", "/api/v1/cuentas/" + CUENTA_ID + "/bloquear", MOTIVO),
                Arguments.of("POST", "/api/v1/cuentas/" + CUENTA_ID + "/desbloquear", MOTIVO),
                Arguments.of("POST", "/api/v1/cuentas/" + CUENTA_ID + "/cerrar", MOTIVO)
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
        verifyNoInteractions(cuentaService, saldoService, retiroService, transferenciaService, estadoCuentaService);
    }
}
