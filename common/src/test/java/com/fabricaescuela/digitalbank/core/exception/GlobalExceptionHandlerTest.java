package com.fabricaescuela.digitalbank.core.exception;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.fabricaescuela.digitalbank.core.web.TraceIdFilter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("GlobalExceptionHandler - cuerpo uniforme de error")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    static class ConflictoDePrueba extends ApiException {
        ConflictoDePrueba() {
            super(HttpStatus.CONFLICT, "CONFLICTO_DE_PRUEBA", "El cliente ya existe");
        }
    }

    static class BloqueoDePrueba extends ApiException {
        BloqueoDePrueba() {
            super(HttpStatus.TOO_MANY_REQUESTS, "BLOQUEO_DE_PRUEBA", "Cuenta bloqueada temporalmente");
        }
    }

    static class NoEncontradaDePrueba extends ApiException {
        NoEncontradaDePrueba() {
            super(HttpStatus.NOT_FOUND, "NO_ENCONTRADA_DE_PRUEBA", "Cuenta no encontrada");
        }
    }

    static class MenorDeEdadDePrueba extends ApiException {
        MenorDeEdadDePrueba() {
            super(HttpStatus.BAD_REQUEST, "MENOR_DE_EDAD_DE_PRUEBA", "El cliente debe ser mayor de edad");
        }
    }

    @Test
    @DisplayName("Una ApiException conserva su estado y su mensaje de negocio")
    void apiExceptionConservaEstadoYMensaje() {
        // Arrange
        MockHttpServletRequest request = peticion("/api/v1/cuentas/1234567890/saldo");

        // Act
        ResponseEntity<Map<String, Object>> respuesta =
                handler.handleApiException(new NoEncontradaDePrueba(), request);

        // Assert
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody())
                .containsEntry("status", 404)
                .containsEntry("error", "Not Found")
                .containsEntry("errorCode", "NO_ENCONTRADA_DE_PRUEBA")
                .containsEntry("message", "Cuenta no encontrada")
                .containsEntry("path", "/api/v1/cuentas/1234567890/saldo");
        assertThat(respuesta.getBody().get("timestamp")).isInstanceOf(Instant.class);
    }

    @Test
    @DisplayName("Cada ApiException traduce su propio codigo HTTP")
    void cadaApiExceptionTraduceSuCodigoHttp() {
        // Arrange
        MockHttpServletRequest request = peticion("/api/v1/clientes");

        // Act
        ResponseEntity<Map<String, Object>> respuesta =
                handler.handleApiException(new MenorDeEdadDePrueba(), request);

        // Assert
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).containsEntry("message", "El cliente debe ser mayor de edad");
    }

    @Test
    @DisplayName("Un error de validacion responde 400 con el mensaje del primer campo invalido")
    void errorDeValidacionRespondeConElPrimerMensaje() throws Exception {
        // Arrange
        MockHttpServletRequest request = peticion("/api/v1/clientes");
        MethodArgumentNotValidException excepcion = errorDeValidacion(
                "telefono", "El teléfono debe tener 10 dígitos");

        // Act
        ResponseEntity<Map<String, Object>> respuesta = handler.handleValidation(excepcion, request);

        // Assert
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody())
                .containsEntry("status", 400)
                .containsEntry("errorCode", "DATOS_INVALIDOS")
                .containsEntry("message", "El teléfono debe tener 10 dígitos")
                .containsEntry("path", "/api/v1/clientes");
    }

    @Test
    @DisplayName("Un error de validacion sin campos concretos usa el mensaje genErico")
    void errorDeValidacionSinCamposUsaMensajeGenerico() throws Exception {
        // Arrange
        MockHttpServletRequest request = peticion("/api/v1/clientes");
        MethodArgumentNotValidException excepcion = errorDeValidacion(null, null);

        // Act
        ResponseEntity<Map<String, Object>> respuesta = handler.handleValidation(excepcion, request);

        // Assert
        assertThat(respuesta.getBody()).containsEntry("message", "Datos inválidos");
    }

    @Test
    @DisplayName("Un JSON ilegible responde 400 sin filtrar detalles internos")
    void jsonIlegibleResponde400() {
        // Arrange
        MockHttpServletRequest request = peticion("/api/v1/auth/login");
        HttpMessageNotReadableException excepcion = new HttpMessageNotReadableException(
                "Unexpected end-of-input at [Source: (String)\"{\"]",
                new MockHttpInputMessage("{".getBytes(StandardCharsets.UTF_8)));

        // Act
        ResponseEntity<Map<String, Object>> respuesta = handler.handleMensajeIlegible(excepcion, request);

        // Assert
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody())
                .containsEntry("errorCode", "SOLICITUD_INVALIDA")
                .containsEntry("message", "Solicitud inválida")
                .containsEntry("path", "/api/v1/auth/login");
        assertThat(respuesta.getBody().get("message").toString()).doesNotContain("Source");
    }

    @Test
    @DisplayName("Un 409 de negocio deja un warn con el estado, la excepcion y la ruta")
    void conflictoDejaUnWarn(CapturedOutput salida) {
        // Act
        handler.handleApiException(new ConflictoDePrueba(), peticion("/api/v1/clientes"));

        // Assert
        assertThat(salida.getAll())
                .contains("WARN")
                .contains("evento=ERROR_NEGOCIO status=409 excepcion=ConflictoDePrueba path=/api/v1/clientes");
    }

    @Test
    @DisplayName("Un 429 de negocio no deja log en el manejador porque ya lo registra el login")
    void unBloqueoNoSeRegistraEnElManejador(CapturedOutput salida) {
        // Act
        handler.handleApiException(new BloqueoDePrueba(), peticion("/api/v1/auth/login"));

        // Assert
        assertThat(salida.getAll()).doesNotContain("evento=ERROR_NEGOCIO");
    }

    @Test
    @DisplayName("Los 400 y 404 no se registran como eventos")
    void erroresDeValidacionYNoEncontradoNoSeRegistran(CapturedOutput salida) throws Exception {
        // Act
        handler.handleApiException(new MenorDeEdadDePrueba(), peticion("/api/v1/clientes"));
        handler.handleApiException(new NoEncontradaDePrueba(), peticion("/api/v1/cuentas/1234567890/saldo"));
        handler.handleValidation(errorDeValidacion("telefono", "El teléfono debe tener 10 dígitos"),
                peticion("/api/v1/clientes"));

        // Assert
        assertThat(salida.getAll()).doesNotContain("evento=");
    }

    private MockHttpServletRequest peticion(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    private MethodArgumentNotValidException errorDeValidacion(String campo, String mensaje) throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        if (campo != null) {
            bindingResult.addError(new FieldError("request", campo, null, false, null, null, mensaje));
        }
        MethodParameter parametro = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("metodoDeReferencia", String.class), 0);
        return new MethodArgumentNotValidException(parametro, bindingResult);
    }

    @SuppressWarnings("unused")
    private void metodoDeReferencia(String payload) {
        // Solo existe para construir un MethodParameter valido en las pruebas.
    }

    @Test
    @DisplayName("El cuerpo del error incluye el traceId de la peticion")
    void elErrorIncluyeElTraceId() {
        // Arrange
        MDC.put(TraceIdFilter.MDC_KEY, "abc-12345678");
        try {
            // Act
            ResponseEntity<Map<String, Object>> respuesta =
                    handler.handleApiException(new NoEncontradaDePrueba(), peticion("/api/v1/cuentas/1"));

            // Assert
            assertThat(respuesta.getBody()).containsEntry("traceId", "abc-12345678");
        } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
        }
    }
}