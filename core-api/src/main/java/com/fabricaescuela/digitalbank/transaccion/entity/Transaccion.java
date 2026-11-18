package com.fabricaescuela.digitalbank.transaccion.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// DynamicUpdate: Hibernate genera el UPDATE solo con las columnas que cambian.
// Al marcar una transacción como REVERSADA solo se tocan estado y transaccion_relacionada_id,
// así alcanza con el permiso de UPDATE por columna del rol de base de datos.
@Entity
@Table(name = "transaccion")
@DynamicUpdate
public class Transaccion {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "cuenta_id", nullable = false)
    private UUID cuentaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoTransaccion tipo;

    @Column(nullable = false)
    private BigDecimal monto;

    @Column(name = "saldo_anterior", nullable = false)
    private BigDecimal saldoAnterior;

    @Column(name = "saldo_nuevo", nullable = false)
    private BigDecimal saldoNuevo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrigenDeposito origen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTransaccion estado = EstadoTransaccion.COMPLETADA;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @Column(name = "transferencia_id")
    private UUID transferenciaId;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SentidoTransferencia sentido;

    @Column(length = 255)
    private String descripcion;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(length = 255)
    private String motivo;

    @Column(name = "transaccion_relacionada_id")
    private UUID transaccionRelacionadaId;

    protected Transaccion() {}

    public static Transaccion deposito(UUID cuentaId, BigDecimal monto, BigDecimal saldoAnterior,
                                        BigDecimal saldoNuevo, OrigenDeposito origen) {
        Transaccion transaccion = new Transaccion();
        transaccion.cuentaId = cuentaId;
        transaccion.tipo = TipoTransaccion.DEPOSITO;
        transaccion.monto = monto;
        transaccion.saldoAnterior = saldoAnterior;
        transaccion.saldoNuevo = saldoNuevo;
        transaccion.origen = origen;
        return transaccion;
    }

    public static Transaccion retiro(UUID cuentaId, BigDecimal monto, BigDecimal saldoAnterior,
                                     BigDecimal saldoNuevo) {
        Transaccion transaccion = new Transaccion();
        transaccion.cuentaId = cuentaId;
        transaccion.tipo = TipoTransaccion.RETIRO;
        transaccion.monto = monto;
        transaccion.saldoAnterior = saldoAnterior;
        transaccion.saldoNuevo = saldoNuevo;
        return transaccion;
    }

    public static Transaccion transferencia(UUID cuentaId, BigDecimal monto, BigDecimal saldoAnterior,
                                            BigDecimal saldoNuevo, UUID transferenciaId,
                                            SentidoTransferencia sentido, String descripcion) {
        Transaccion transaccion = new Transaccion();
        transaccion.cuentaId = cuentaId;
        transaccion.tipo = TipoTransaccion.TRANSFERENCIA;
        transaccion.monto = monto;
        transaccion.saldoAnterior = saldoAnterior;
        transaccion.saldoNuevo = saldoNuevo;
        transaccion.transferenciaId = transferenciaId;
        transaccion.sentido = sentido;
        transaccion.descripcion = descripcion;
        return transaccion;
    }

    public static Transaccion reverso(UUID cuentaId, BigDecimal monto, BigDecimal saldoAnterior,
                                      BigDecimal saldoNuevo, SentidoTransferencia sentido,
                                      UUID transferenciaId, UUID transaccionRelacionadaId,
                                      UUID usuarioId, String motivo) {
        Transaccion transaccion = new Transaccion();
        transaccion.cuentaId = cuentaId;
        transaccion.tipo = TipoTransaccion.REVERSO;
        transaccion.monto = monto;
        transaccion.saldoAnterior = saldoAnterior;
        transaccion.saldoNuevo = saldoNuevo;
        transaccion.sentido = sentido;
        transaccion.transferenciaId = transferenciaId;
        transaccion.transaccionRelacionadaId = transaccionRelacionadaId;
        transaccion.usuarioId = usuarioId;
        transaccion.motivo = motivo;
        return transaccion;
    }

    public boolean estaCompletada() {
        return estado == EstadoTransaccion.COMPLETADA;
    }

    public void marcarReversada(UUID reversoId) {
        this.estado = EstadoTransaccion.REVERSADA;
        this.transaccionRelacionadaId = reversoId;
    }

    public UUID getId() { return id; }
    public UUID getCuentaId() { return cuentaId; }
    public TipoTransaccion getTipo() { return tipo; }
    public BigDecimal getMonto() { return monto; }
    public BigDecimal getSaldoAnterior() { return saldoAnterior; }
    public BigDecimal getSaldoNuevo() { return saldoNuevo; }
    public OrigenDeposito getOrigen() { return origen; }
    public EstadoTransaccion getEstado() { return estado; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public UUID getTransferenciaId() { return transferenciaId; }
    public SentidoTransferencia getSentido() { return sentido; }
    public String getDescripcion() { return descripcion; }
    public UUID getUsuarioId() { return usuarioId; }
    public String getMotivo() { return motivo; }
    public UUID getTransaccionRelacionadaId() { return transaccionRelacionadaId; }
}
