package com.fabricaescuela.digitalbank.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class Usuario {

    private static final int MAX_INTENTOS_FALLIDOS = 3;
    private static final long HORAS_BLOQUEO = 1;

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "cliente_id")
    private UUID clienteId;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_seguridad", nullable = false)
    private EstadoSeguridad estadoSeguridad = EstadoSeguridad.ACTIVO;

    @Column(name = "fecha_bloqueo")
    private LocalDateTime fechaBloqueo;

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    protected Usuario() {}

    public Usuario(UUID clienteId, String email, String passwordHash, Rol rol) {
        this.clienteId = clienteId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.rol = rol;
    }


    // Comportamiento de negocio

    public boolean estaBloqueado() {
        if (estadoSeguridad != EstadoSeguridad.BLOQUEADO_TEMPORAL) {
            return false;
        }
        LocalDateTime finBloqueo = fechaBloqueo.plusHours(HORAS_BLOQUEO);
        return LocalDateTime.now().isBefore(finBloqueo);
    }

    public boolean bloqueoExpiro() {
        return estadoSeguridad == EstadoSeguridad.BLOQUEADO_TEMPORAL && !estaBloqueado();
    }

    public void desbloquear() {
        this.estadoSeguridad = EstadoSeguridad.ACTIVO;
        this.intentosFallidos = 0;
        this.fechaBloqueo = null;
    }

    public void registrarIntentoFallido() {
        this.intentosFallidos++;
        if (this.intentosFallidos >= MAX_INTENTOS_FALLIDOS) {
            this.estadoSeguridad = EstadoSeguridad.BLOQUEADO_TEMPORAL;
            this.fechaBloqueo = LocalDateTime.now();
        }
    }

    public void registrarLoginExitoso() {
        this.intentosFallidos = 0;
        this.estadoSeguridad = EstadoSeguridad.ACTIVO;
        this.fechaBloqueo = null;
        this.ultimoLogin = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getClienteId() { return clienteId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Rol getRol() { return rol; }
    public int getIntentosFallidos() { return intentosFallidos; }
    public EstadoSeguridad getEstadoSeguridad() { return estadoSeguridad; }
}