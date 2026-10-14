package com.fabricaescuela.digitalbank.cuenta.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "movimiento_administrativo_cuenta")
public class MovimientoAdministrativoCuenta {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "cuenta_id", nullable = false)
    private UUID cuentaId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_accion", nullable = false, length = 15)
    private TipoAccionCuenta tipoAccion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", nullable = false, length = 20)
    private EstadoCuenta estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private EstadoCuenta estadoNuevo;

    @Column(nullable = false, length = 255)
    private String motivo;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    protected MovimientoAdministrativoCuenta() {}

    private MovimientoAdministrativoCuenta(UUID cuentaId, UUID usuarioId, TipoAccionCuenta tipoAccion,
                                        EstadoCuenta estadoAnterior, EstadoCuenta estadoNuevo,
                                        String motivo) {
        this.cuentaId = cuentaId;
        this.usuarioId = usuarioId;
        this.tipoAccion = tipoAccion;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.motivo = motivo;
    }

    public static MovimientoAdministrativoCuenta registrar(UUID cuentaId, UUID usuarioId,
                                                        TipoAccionCuenta tipoAccion,
                                                        EstadoCuenta estadoAnterior,
                                                        EstadoCuenta estadoNuevo,
                                                        String motivo) {
        return new MovimientoAdministrativoCuenta(cuentaId, usuarioId, tipoAccion,
                estadoAnterior, estadoNuevo, motivo);
    }

    public UUID getId() { return id; }
    public UUID getCuentaId() { return cuentaId; }
    public UUID getUsuarioId() { return usuarioId; }
    public TipoAccionCuenta getTipoAccion() { return tipoAccion; }
    public EstadoCuenta getEstadoAnterior() { return estadoAnterior; }
    public EstadoCuenta getEstadoNuevo() { return estadoNuevo; }
    public String getMotivo() { return motivo; }
    public LocalDateTime getFechaHora() { return fechaHora; }
}