package com.fabricaescuela.digitalbank.cliente.service;

import com.fabricaescuela.digitalbank.cliente.dto.InactivacionRegistrada;
import com.fabricaescuela.digitalbank.cliente.entity.EstadoCliente;
import com.fabricaescuela.digitalbank.cliente.entity.MovimientoAdministrativoCliente;
import com.fabricaescuela.digitalbank.cliente.interfaces.repositories.IMovimientoAdministrativoClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// La tabla cliente es de auth-api y no la crea Hibernate en las pruebas de core-api,
// así que aquí se crea a mano en un H2 propio con solo las columnas que se usan.
@DisplayName("ClienteEstadoService - estado del cliente visto desde core-api (HU-04)")
class ClienteEstadoServiceTest {

    private JdbcClient jdbcClient;
    private IMovimientoAdministrativoClienteRepository movimientoRepository;
    private ClienteEstadoService service;

    @BeforeEach
    void setUp() {
        jdbcClient = JdbcClient.create(new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build());
        jdbcClient.sql("CREATE TABLE cliente (id UUID PRIMARY KEY, estado VARCHAR(20) NOT NULL)").update();
        movimientoRepository = mock(IMovimientoAdministrativoClienteRepository.class);
        service = new ClienteEstadoService(jdbcClient, movimientoRepository);
    }

    @Test
    @DisplayName("estaInactivo es true solo para un cliente INACTIVO")
    void estaInactivoSegunElEstado() {
        UUID activo = crearCliente("ACTIVO");
        UUID inactivo = crearCliente("INACTIVO");

        assertThat(service.estaInactivo(activo)).isFalse();
        assertThat(service.estaInactivo(inactivo)).isTrue();
        assertThat(service.estaInactivo(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("bloquearYObtenerEstado devuelve el estado, o vacío si el cliente no existe")
    void bloquearYObtenerEstado() {
        UUID activo = crearCliente("ACTIVO");

        assertThat(service.bloquearYObtenerEstado(activo)).contains(EstadoCliente.ACTIVO);
        assertThat(service.bloquearYObtenerEstado(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("inactivar cambia el estado a INACTIVO y guarda la auditoría")
    void inactivarCambiaElEstadoYGuardaLaAuditoria() {
        UUID clienteId = crearCliente("ACTIVO");
        UUID adminId = UUID.randomUUID();
        String motivo = "Solicitud del cliente por cambio de banco";
        when(movimientoRepository.save(any(MovimientoAdministrativoCliente.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<MovimientoAdministrativoCliente> captor =
                ArgumentCaptor.forClass(MovimientoAdministrativoCliente.class);

        InactivacionRegistrada registro = service.inactivar(clienteId, adminId, motivo);

        assertThat(service.estaInactivo(clienteId)).isTrue();
        verify(movimientoRepository).save(captor.capture());
        MovimientoAdministrativoCliente movimiento = captor.getValue();
        assertThat(movimiento.getId()).isNull();
        assertThat(movimiento.getClienteId()).isEqualTo(clienteId);
        assertThat(movimiento.getUsuarioId()).isEqualTo(adminId);
        assertThat(movimiento.getEstadoAnterior()).isEqualTo(EstadoCliente.ACTIVO);
        assertThat(movimiento.getEstadoNuevo()).isEqualTo(EstadoCliente.INACTIVO);
        assertThat(movimiento.getMotivo()).isEqualTo(motivo);
        assertThat(registro.usuarioId()).isEqualTo(adminId);
        assertThat(registro.motivo()).isEqualTo(motivo);
        assertThat(registro.fechaHora()).isEqualTo(movimiento.getFechaHora());
    }

    private UUID crearCliente(String estado) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO cliente (id, estado) VALUES (:id, :estado)")
                .param("id", id)
                .param("estado", estado)
                .update();
        return id;
    }
}
