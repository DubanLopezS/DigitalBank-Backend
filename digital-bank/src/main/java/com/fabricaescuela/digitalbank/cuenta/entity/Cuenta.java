package com.fabricaescuela.digitalbank.cuenta.entity;

import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "numero_cuenta", nullable = false, length = 10)
    private String numeroCuenta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cuenta", nullable = false, length = 20)
    private TipoCuenta tipoCuenta;

    @Column(name = "saldo_contable", nullable = false)
    private BigDecimal saldoContable;

    @Column(nullable = false)
    private BigDecimal retencion = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuenta estado = EstadoCuenta.ACTIVA;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura = LocalDateTime.now();

    protected Cuenta() {}

    public Cuenta(Cliente cliente, String numeroCuenta, TipoCuenta tipoCuenta, BigDecimal saldoInicial) {
        this.cliente = cliente;
        this.numeroCuenta = numeroCuenta;
        this.tipoCuenta = tipoCuenta;
        this.saldoContable = saldoInicial;
    }

    public UUID getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public String getNumeroCuenta() { return numeroCuenta; }
    public TipoCuenta getTipoCuenta() { return tipoCuenta; }
    public BigDecimal getSaldoContable() { return saldoContable; }
    public BigDecimal getRetencion() { return retencion; }
    public EstadoCuenta getEstado() { return estado; }
    public LocalDateTime getFechaApertura() { return fechaApertura; }

    public BigDecimal getSaldoDisponible() {
        return saldoContable.subtract(retencion);
    }
}