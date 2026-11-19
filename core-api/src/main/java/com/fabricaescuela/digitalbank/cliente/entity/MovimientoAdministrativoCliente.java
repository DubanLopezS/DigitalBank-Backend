package com.fabricaescuela.digitalbank.cliente.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

// Auditoría de la inactivación de un cliente (HU-04). La tabla la crea V6.
@Entity
@Table(name = "movimiento_administrativo_cliente")
public class MovimientoAdministrativoCliente {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", nullable = false, length = 20)
    private EstadoCliente estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private EstadoCliente estadoNuevo;

    @Column(nullable = false, length = 255)
    private String motivo;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    protected MovimientoAdministrativoCliente() {}

    private MovimientoAdministrativoCliente(UUID clienteId, UUID usuarioId, String motivo) {
        this.clienteId = clienteId;
        this.usuarioId = usuarioId;
        this.estadoAnterior = EstadoCliente.ACTIVO;
        this.estadoNuevo = EstadoCliente.INACTIVO;
        this.motivo = motivo;
    }

    public static MovimientoAdministrativoCliente inactivacion(UUID clienteId, UUID usuarioId, String motivo) {
        return new MovimientoAdministrativoCliente(clienteId, usuarioId, motivo);
    }

    public UUID getId() { return id; }
    public UUID getClienteId() { return clienteId; }
    public UUID getUsuarioId() { return usuarioId; }
    public EstadoCliente getEstadoAnterior() { return estadoAnterior; }
    public EstadoCliente getEstadoNuevo() { return estadoNuevo; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getFechaHora() { return fechaHora; }
}
