package com.fabricaescuela.digitalbank.cliente.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro de un cambio en un dato de contacto del cliente (HU-03).
 * Mapea la tabla auditoria_datos_cliente creada en la V6.
 */
@Entity
@Table(name = "auditoria_datos_cliente")
public class AuditoriaDatosCliente {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "campo", nullable = false, length = 20)
    private CampoAuditado campo;

    @Column(name = "valor_anterior", nullable = false, length = 255)
    private String valorAnterior;

    @Column(name = "valor_nuevo", nullable = false, length = 255)
    private String valorNuevo;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    protected AuditoriaDatosCliente() {}

    public AuditoriaDatosCliente(UUID clienteId, UUID usuarioId, CampoAuditado campo,
                                 String valorAnterior, String valorNuevo) {
        this.clienteId = clienteId;
        this.usuarioId = usuarioId;
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
    }

    public UUID getId() { return id; }
    public UUID getClienteId() { return clienteId; }
    public UUID getUsuarioId() { return usuarioId; }
    public CampoAuditado getCampo() { return campo; }
    public String getValorAnterior() { return valorAnterior; }
    public String getValorNuevo() { return valorNuevo; }
    public LocalDateTime getFechaHora() { return fechaHora; }
}
