-- Auditoría de acciones administrativas sobre una cuenta 
CREATE TABLE movimiento_administrativo_cuenta (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cuenta_id        UUID         NOT NULL REFERENCES cuenta(id),
    -- usuario_id no es FK: la tabla usuario es de auth-api, no de core-api.
    usuario_id       UUID         NOT NULL,
    tipo_accion      VARCHAR(15)  NOT NULL,
    estado_anterior  VARCHAR(20)  NOT NULL,
    estado_nuevo     VARCHAR(20)  NOT NULL,
    motivo           VARCHAR(255) NOT NULL,
    fecha_hora       TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_mov_admin_tipo_accion CHECK (tipo_accion IN ('BLOQUEO','DESBLOQUEO','CIERRE')),
    CONSTRAINT ck_mov_admin_estado_anterior CHECK (estado_anterior IN ('ACTIVA','BLOQUEADA','CERRADA')),
    CONSTRAINT ck_mov_admin_estado_nuevo CHECK (estado_nuevo IN ('ACTIVA','BLOQUEADA','CERRADA')),
    CONSTRAINT ck_mov_admin_motivo CHECK (char_length(btrim(motivo)) >= 10)
);

CREATE INDEX idx_mov_admin_cuenta ON movimiento_administrativo_cuenta(cuenta_id);
CREATE INDEX idx_mov_admin_usuario ON movimiento_administrativo_cuenta(usuario_id);