-- Auditoría de cambios de datos de contacto del cliente (HU-03)
CREATE TABLE auditoria_datos_cliente (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id      UUID         NOT NULL REFERENCES cliente(id),
    -- usuario_id no es FK: la tabla usuario es de auth-api, no de core-api.
    usuario_id      UUID         NOT NULL,
    campo           VARCHAR(20)  NOT NULL,
    valor_anterior  VARCHAR(255) NOT NULL,
    valor_nuevo     VARCHAR(255) NOT NULL,
    fecha_hora      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_aud_datos_cliente_campo CHECK (campo IN ('EMAIL','TELEFONO'))
);

CREATE INDEX idx_aud_datos_cliente_cliente ON auditoria_datos_cliente(cliente_id);
CREATE INDEX idx_aud_datos_cliente_usuario ON auditoria_datos_cliente(usuario_id);

-- Auditoría de la inactivación de un cliente (HU-04). No mueve dinero.
CREATE TABLE movimiento_administrativo_cliente (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id       UUID         NOT NULL REFERENCES cliente(id),
    usuario_id       UUID         NOT NULL,
    estado_anterior  VARCHAR(20)  NOT NULL,
    estado_nuevo     VARCHAR(20)  NOT NULL,
    motivo           VARCHAR(255) NOT NULL,
    fecha_hora       TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_mov_admin_cliente_estado_anterior CHECK (estado_anterior = 'ACTIVO'),
    CONSTRAINT ck_mov_admin_cliente_estado_nuevo CHECK (estado_nuevo = 'INACTIVO'),
    CONSTRAINT ck_mov_admin_cliente_motivo CHECK (char_length(btrim(motivo)) >= 10)
);

CREATE INDEX idx_mov_admin_cliente_cliente ON movimiento_administrativo_cliente(cliente_id);
CREATE INDEX idx_mov_admin_cliente_usuario ON movimiento_administrativo_cliente(usuario_id);

-- HU-14 y HU-12: usuario que originó la transacción (nullable: las anteriores no lo tienen).
-- No es FK: la tabla usuario es de auth-api.
ALTER TABLE transaccion
    ADD COLUMN usuario_id UUID,
    ADD COLUMN motivo VARCHAR(255),
    ADD COLUMN transaccion_relacionada_id UUID REFERENCES transaccion(id);

-- Un reverso apunta a su original y la original REVERSADA apunta a su reverso.
-- UNIQUE: una transacción solo puede ser reversada una vez.
ALTER TABLE transaccion
    ADD CONSTRAINT uk_transaccion_relacionada UNIQUE (transaccion_relacionada_id);

ALTER TABLE transaccion
    DROP CONSTRAINT ck_transaccion_tipo,
    DROP CONSTRAINT ck_transaccion_estado,
    DROP CONSTRAINT ck_transaccion_transferencia;

ALTER TABLE transaccion
    ADD CONSTRAINT ck_transaccion_tipo
        CHECK (tipo IN ('DEPOSITO','RETIRO','TRANSFERENCIA','REVERSO')),
    ADD CONSTRAINT ck_transaccion_estado
        CHECK (estado IN ('COMPLETADA','REVERSADA')),
    -- TRANSFERENCIA: transferencia_id y sentido obligatorios.
    -- REVERSO: sentido obligatorio, transferencia_id opcional.
    -- DEPOSITO y RETIRO: ambos NULL.
    ADD CONSTRAINT ck_transaccion_transferencia CHECK (
        (tipo = 'TRANSFERENCIA' AND transferencia_id IS NOT NULL AND sentido IS NOT NULL)
        OR (tipo = 'REVERSO' AND sentido IS NOT NULL)
        OR (tipo IN ('DEPOSITO','RETIRO') AND transferencia_id IS NULL AND sentido IS NULL)
    ),
    -- El REVERSO exige original, usuario y motivo de mínimo 10 caracteres.
    -- El motivo solo existe en los reversos.
    ADD CONSTRAINT ck_transaccion_reverso CHECK (
        (tipo = 'REVERSO'
            AND transaccion_relacionada_id IS NOT NULL
            AND usuario_id IS NOT NULL
            AND motivo IS NOT NULL
            AND char_length(btrim(motivo)) >= 10)
        OR (tipo <> 'REVERSO' AND motivo IS NULL)
    ),
    -- Un REVERSO nunca pasa a REVERSADA, y una REVERSADA siempre apunta a su reverso.
    ADD CONSTRAINT ck_transaccion_reversada CHECK (
        estado = 'COMPLETADA'
        OR (estado = 'REVERSADA' AND tipo <> 'REVERSO' AND transaccion_relacionada_id IS NOT NULL)
    );