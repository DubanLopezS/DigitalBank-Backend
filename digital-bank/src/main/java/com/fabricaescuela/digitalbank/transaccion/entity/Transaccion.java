package com.fabricaescuela.digitalbank.transaccion.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaccion")
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
}
