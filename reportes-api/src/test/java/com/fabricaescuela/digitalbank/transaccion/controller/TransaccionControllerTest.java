package com.fabricaescuela.digitalbank.transaccion.controller;

import com.fabricaescuela.digitalbank.core.config.SecurityConfig;
import com.fabricaescuela.digitalbank.core.exception.GlobalExceptionHandler;
import com.fabricaescuela.digitalbank.core.security.IJwtService;
import com.fabricaescuela.digitalbank.core.security.JwtAuthenticationFilter;
import com.fabricaescuela.digitalbank.core.web.ApiAccessDeniedHandler;
import com.fabricaescuela.digitalbank.core.web.ApiAuthenticationEntryPoint;
import com.fabricaescuela.digitalbank.core.web.ErrorResponseWriter;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccion;
import com.fabricaescuela.digitalbank.transaccion.dto.DetalleTransaccion.CuentaTransaccion;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import com.fabricaescuela.digitalbank.transaccion.service.TransaccionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransaccionController.class)
@ImportAutoConfiguration(SecurityFilterAutoConfiguration.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class,
        TransaccionServiceImpl.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ErrorResponseWriter.class})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("TransaccionController - detalle de una transaccion por HTTP con la seguridad real")
class TransaccionControllerTest {

    private static final UUID TRANSACCION_ID = UUID.randomUUID();
    private static final String RUTA = "/api/v1/reportes/transacciones/" + TRANSACCION_ID;
    private static final String TOKEN = "token-de-prueba";
    private static final UUID CLIENTE_ORIGEN = UUID.randomUUID();
    private static final UUID USUARIO_ORIGINADOR = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJwtService jwtService;

    @MockitoBean
    private ITransaccionRepository transaccionRepository;

    @Test
    @DisplayName("Sin token responde 401 NO_AUTENTICADO y no se consulta la base")
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.path").value(RUTA));
        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("Un CAJERO recibe 403 ACCESO_NO_AUTORIZADO y no se consulta la base")
    void cajeroRecibe403() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "CAJERO", null);

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_NO_AUTORIZADO"))
                .andExpect(jsonPath("$.message").value("Acceso no autorizado"));
        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("Un id que no es UUID responde 400 PARAMETRO_INVALIDO")
    void idQueNoEsUuidResponde400() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "ADMIN", null);

        // Act + Assert
        mockMvc.perform(get("/api/v1/reportes/transacciones/no-es-un-uuid").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
        verifyNoInteractions(transaccionRepository);
    }

    @Test
    @DisplayName("El JSON que recibe el CLIENTE trae el detalle pero no el campo usuarioId")
    void clienteRecibeElDetalleSinUsuarioId() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "CLIENTE", CLIENTE_ORIGEN);
        when(transaccionRepository.findDetalleById(TRANSACCION_ID)).thenReturn(Optional.of(transferencia(USUARIO_ORIGINADOR)));

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TRANSACCION_ID.toString()))
                .andExpect(jsonPath("$.tipo").value("TRANSFERENCIA"))
                .andExpect(jsonPath("$.monto").value(30000.00))
                .andExpect(jsonPath("$.estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.fechaHora").value("2026-10-05T10:15:30.123"))
                .andExpect(jsonPath("$.numeroCuentaOrigen").value("1111111111"))
                .andExpect(jsonPath("$.numeroCuentaDestino").value("2222222222"))
                .andExpect(jsonPath("$.descripcion").value("Pago arriendo"))
                .andExpect(jsonPath("$.usuarioId").doesNotExist())
                .andExpect(content().string(not(containsString("usuarioId"))));
    }

    @Test
    @DisplayName("El JSON que recibe el ADMIN trae el campo usuarioId plano junto al detalle")
    void adminRecibeElUsuarioId() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "ADMIN", null);
        when(transaccionRepository.findDetalleById(TRANSACCION_ID)).thenReturn(Optional.of(transferencia(USUARIO_ORIGINADOR)));

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TRANSACCION_ID.toString()))
                .andExpect(jsonPath("$.numeroCuentaDestino").value("2222222222"))
                .andExpect(jsonPath("$.usuarioId").value(USUARIO_ORIGINADOR.toString()))
                .andExpect(jsonPath("$.detalle").doesNotExist());
    }

    @Test
    @DisplayName("En una transaccion sin usuario el ADMIN recibe el campo usuarioId en null")
    void adminRecibeUsuarioIdNuloEnTransaccionVieja() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "ADMIN", null);
        when(transaccionRepository.findDetalleById(TRANSACCION_ID)).thenReturn(Optional.of(transferencia(null)));

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"usuarioId\":null")));
    }

    @Test
    @DisplayName("Un ADMIN que consulta una transaccion inexistente recibe 404 TRANSACCION_NO_ENCONTRADA")
    void adminConTransaccionInexistenteRecibe404() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "ADMIN", null);
        when(transaccionRepository.findDetalleById(TRANSACCION_ID)).thenReturn(Optional.empty());

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSACCION_NO_ENCONTRADA"))
                .andExpect(jsonPath("$.message").value("Transacción no encontrada"));
    }

    @Test
    @DisplayName("Un CLIENTE con una transaccion inexistente recibe 403 y queda el evento de acceso denegado")
    void clienteConTransaccionInexistenteRecibe403YDejaElEvento(CapturedOutput salida) throws Exception {
        // Arrange
        UUID usuarioId = UUID.randomUUID();
        autenticarComo(usuarioId, "CLIENTE", CLIENTE_ORIGEN);
        when(transaccionRepository.findDetalleById(TRANSACCION_ID)).thenReturn(Optional.empty());

        // Act
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_NO_AUTORIZADO"));

        // Assert
        assertThat(salida.getAll())
                .contains("evento=ACCESO_TRANSACCION_DENEGADO status=403 transaccionId=" + TRANSACCION_ID
                        + " usuarioId=" + usuarioId + " rol=CLIENTE")
                .doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("Un CLIENTE con una transaccion ajena recibe 403 ACCESO_NO_AUTORIZADO")
    void clienteConTransaccionAjenaRecibe403() throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "CLIENTE", UUID.randomUUID());
        when(transaccionRepository.findDetalleById(TRANSACCION_ID)).thenReturn(Optional.of(transferencia(USUARIO_ORIGINADOR)));

        // Act + Assert
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_NO_AUTORIZADO"));
    }

    @Test
    @DisplayName("El 404 a un ADMIN por transaccion inexistente no se registra como acceso denegado")
    void adminConTransaccionInexistenteNoDejaEventoDeAccesoDenegado(CapturedOutput salida) throws Exception {
        // Arrange
        autenticarComo(UUID.randomUUID(), "ADMIN", null);
        when(transaccionRepository.findDetalleById(TRANSACCION_ID)).thenReturn(Optional.empty());

        // Act
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());

        // Assert
        assertThat(salida.getAll()).doesNotContain("evento=ACCESO_TRANSACCION_DENEGADO");
    }

    private void autenticarComo(UUID usuarioId, String rol, UUID clienteId) {
        when(jwtService.esTokenValido(TOKEN)).thenReturn(true);
        when(jwtService.extraerUsuarioId(TOKEN)).thenReturn(usuarioId);
        when(jwtService.extraerRol(TOKEN)).thenReturn(rol);
        when(jwtService.extraerClienteId(TOKEN)).thenReturn(clienteId);
    }

    private DetalleTransaccion transferencia(UUID usuarioId) {
        return new DetalleTransaccion(TRANSACCION_ID, "TRANSFERENCIA", new BigDecimal("30000.00"), "COMPLETADA",
                LocalDateTime.of(2026, 10, 5, 10, 15, 30, 123_000_000),
                new CuentaTransaccion("1111111111", CLIENTE_ORIGEN),
                new CuentaTransaccion("2222222222", UUID.randomUUID()),
                null, null, "Pago arriendo", usuarioId);
    }
}
