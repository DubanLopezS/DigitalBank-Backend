package com.fabricaescuela.digitalbank.cliente.service;

import com.fabricaescuela.digitalbank.auth.interfaces.CredencialesService;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteRegistroRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResponse;
import com.fabricaescuela.digitalbank.cliente.entity.EstadoCliente;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteMenorDeEdadException;
import com.fabricaescuela.digitalbank.cliente.exception.ClienteYaExisteException;
import com.fabricaescuela.digitalbank.cliente.repository.ClienteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceImplTest {

    private static final String DOCUMENTO = "1020304050";
    private static final String EMAIL = "cliente@example.com";
    private static final String PASSWORD = "Clave1234";

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private CredencialesService credencialesService;

    @InjectMocks
    private ClienteServiceImpl clienteService;

    @Test
    @DisplayName("Con datos validos el cliente se registra exitosamente y se crean sus credenciales")
    void registrarCliente_datosValidos_debeRegistrarExitosamente() {
        when(clienteRepository.existsByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(false);
        when(clienteRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(clienteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ClienteResponse response = clienteService.registrarCliente(request(LocalDate.now().minusYears(25)));

        assertEquals("Juan", response.nombres());
        assertEquals("Perez", response.apellidos());
        assertEquals(EMAIL, response.email());
        assertEquals(EstadoCliente.ACTIVO.name(), response.estado());
        verify(credencialesService).registrarCredencialesCliente(isNull(), eq(EMAIL), eq(PASSWORD));
    }

    @Test
    @DisplayName("Documento duplicado responde con ClienteYaExisteException y no persiste nada")
    void registrarCliente_documentoDuplicado_debeLanzarExcepcion() {
        when(clienteRepository.existsByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(true);

        ClienteYaExisteException ex = assertThrows(
                ClienteYaExisteException.class,
                () -> clienteService.registrarCliente(request(LocalDate.now().minusYears(25)))
        );

        assertEquals("El cliente ya existe", ex.getMessage());
        verify(clienteRepository, never()).save(any());
        verifyNoInteractions(credencialesService);
    }

    @Test
    @DisplayName("Correo duplicado responde con ClienteYaExisteException y no persiste nada")
    void registrarCliente_correoDuplicado_debeLanzarExcepcion() {
        when(clienteRepository.existsByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(false);
        when(clienteRepository.existsByEmail(EMAIL)).thenReturn(true);

        ClienteYaExisteException ex = assertThrows(
                ClienteYaExisteException.class,
                () -> clienteService.registrarCliente(request(LocalDate.now().minusYears(25)))
        );

        assertEquals("El cliente ya existe", ex.getMessage());
        verify(clienteRepository, never()).save(any());
        verifyNoInteractions(credencialesService);
    }

    @Test
    @DisplayName("Cliente menor de edad responde con ClienteMenorDeEdadException y no persiste nada")
    void registrarCliente_menorDeEdad_debeLanzarExcepcion() {
        when(clienteRepository.existsByTipoDocumentoAndNumeroDocumento(TipoDocumento.CC, DOCUMENTO))
                .thenReturn(false);
        when(clienteRepository.existsByEmail(EMAIL)).thenReturn(false);

        ClienteMenorDeEdadException ex = assertThrows(
                ClienteMenorDeEdadException.class,
                () -> clienteService.registrarCliente(request(LocalDate.now().minusYears(10)))
        );

        assertEquals("El cliente debe ser mayor de edad", ex.getMessage());
        verify(clienteRepository, never()).save(any());
        verifyNoInteractions(credencialesService);
    }

    private ClienteRegistroRequest request(LocalDate fechaNacimiento) {
        return new ClienteRegistroRequest(
                TipoDocumento.CC,
                DOCUMENTO,
                "Juan",
                "Perez",
                fechaNacimiento,
                "3001234567",
                EMAIL,
                PASSWORD
        );
    }
}
