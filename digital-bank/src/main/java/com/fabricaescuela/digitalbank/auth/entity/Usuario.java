package com.fabricaescuela.digitalbank.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class Usuario {

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

    public UUID getId() { return id; }
    public UUID getClienteId() { return clienteId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Rol getRol() { return rol; }
}
