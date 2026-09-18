package com.fabricaescuela.digitalbank.cuenta.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cuenta")
public class Cuenta {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "numero_cuenta", nullable = false, length = 10)
    private String numeroCuenta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cuenta", nullable = false, length = 20)
    private TipoCuenta tipoCuenta;

    @Column(name = "saldo_contable", nullable = false)
    private BigDecimal saldoContable = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal retencion = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuenta estado = EstadoCuenta.ACTIVA;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura = LocalDateTime.now();

    protected Cuenta() {}

    public Cuenta(UUID clienteId, String numeroCuenta, TipoCuenta tipoCuenta) {
        this.clienteId = clienteId;
        this.numeroCuenta = numeroCuenta;
        this.tipoCuenta = tipoCuenta;
    }

    // Comportamiento de negocio

    public boolean estaCerrada() {
        return estado == EstadoCuenta.CERRADA;
    }

    public void acreditar(BigDecimal monto) {
        this.saldoContable = this.saldoContable.add(monto);
    }

    public UUID getId() { return id; }
    public UUID getClienteId() { return clienteId; }
    public String getNumeroCuenta() { return numeroCuenta; }
    public TipoCuenta getTipoCuenta() { return tipoCuenta; }
    public BigDecimal getSaldoContable() { return saldoContable; }
    public BigDecimal getRetencion() { return retencion; }
    public EstadoCuenta getEstado() { return estado; }
    public LocalDateTime getFechaApertura() { return fechaApertura; }
}
