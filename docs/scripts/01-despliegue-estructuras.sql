-- =====================================================================
-- Digital Bank - Script de despliegue de estructuras (Sprint 3)
--
-- Qué es:
--   El estado final del modelo físico después de las migraciones
--   V1 a V7 de core-api, escrito en un solo script. Crea las siete
--   tablas con sus llaves primarias, llaves foráneas, restricciones
--   UNIQUE y CHECK, e índices. Los nombres de las restricciones son
--   los mismos que dejan las migraciones.
--
-- Cuándo usarlo:
--   Para montar la base desde cero en un entorno que no administra
--   Flyway (una base local de pruebas, una copia para revisión o un
--   proyecto nuevo de Supabase que se va a poblar a mano).
--
-- Cuándo no usarlo:
--   En la base que usa core-api. Allí la estructura la crea Flyway
--   con V1 a V7. core-api tiene spring.flyway.baseline-on-migrate=true,
--   así que si encuentra tablas sin historial de Flyway marca la base
--   en la versión 1 y luego intenta aplicar V2 a V7 sobre tablas que
--   ya las tienen, y falla. Cualquier cambio de estructura sigue
--   entrando como una migración nueva (V8, ...), y este script se
--   actualiza después para reflejarla.
--
-- Ejecución:
--   psql "<cadena de conexión>" -v ON_ERROR_STOP=1 -f 01-despliegue-estructuras.sql
--   Todo corre en una transacción: si algo falla no queda nada a medias.
-- =====================================================================

BEGIN;

-- gen_random_uuid() viene incluida en PostgreSQL 13+. pgcrypto solo se
-- necesita para crypt()/gen_salt() del script crear-usuarios-internos.sql.
-- En Supabase ya está habilitada (esquema extensions).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------
-- cliente: persona natural registrada en el banco (HU-01, auth-api)
-- El correo se guarda siempre en minúsculas (V5).
-- ---------------------------------------------------------------------
CREATE TABLE cliente (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_documento   VARCHAR(20)  NOT NULL,
    numero_documento VARCHAR(30)  NOT NULL,
    nombres          VARCHAR(100) NOT NULL,
    apellidos        VARCHAR(100) NOT NULL,
    fecha_nacimiento DATE         NOT NULL,
    telefono         VARCHAR(20),
    email            VARCHAR(150) NOT NULL,
    estado           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
    fecha_registro   TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_cliente_documento UNIQUE (tipo_documento, numero_documento),
    CONSTRAINT uk_cliente_email     UNIQUE (email),
    CONSTRAINT ck_cliente_estado    CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT ck_cliente_email_minusculas CHECK (email = lower(email))
);

-- ---------------------------------------------------------------------
-- usuario: credenciales de acceso (HU-01 y login HU-18, auth-api)
-- cliente_id es NULL para los usuarios internos (ADMIN, CAJERO).
-- Un cliente tiene como máximo un usuario (uk_usuario_cliente).
-- El correo se guarda siempre en minúsculas (V5).
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id        UUID REFERENCES cliente (id),
    email             VARCHAR(150) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    rol               VARCHAR(20)  NOT NULL DEFAULT 'CLIENTE',
    intentos_fallidos INTEGER      NOT NULL DEFAULT 0,
    estado_seguridad  VARCHAR(30)  NOT NULL DEFAULT 'ACTIVO',
    fecha_bloqueo     TIMESTAMP,
    ultimo_login      TIMESTAMP,
    CONSTRAINT uk_usuario_email      UNIQUE (email),
    CONSTRAINT uk_usuario_cliente    UNIQUE (cliente_id),
    CONSTRAINT ck_usuario_rol        CHECK (rol IN ('CLIENTE', 'ADMIN', 'CAJERO')),
    CONSTRAINT ck_usuario_estado_seg CHECK (estado_seguridad IN ('ACTIVO', 'BLOQUEADO_TEMPORAL')),
    CONSTRAINT ck_usuario_email_minusculas CHECK (email = lower(email))
);

-- ---------------------------------------------------------------------
-- cuenta: cuenta de ahorros o corriente de un cliente (HU-05)
-- saldo disponible = saldo_contable - retencion (se calcula en la app)
-- ---------------------------------------------------------------------
CREATE TABLE cuenta (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id     UUID          NOT NULL REFERENCES cliente (id),
    numero_cuenta  VARCHAR(10)   NOT NULL,
    tipo_cuenta    VARCHAR(20)   NOT NULL,
    saldo_contable NUMERIC(15,2) NOT NULL DEFAULT 0,
    retencion      NUMERIC(15,2) NOT NULL DEFAULT 0,
    estado         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVA',
    fecha_apertura TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_cuenta_numero                UNIQUE (numero_cuenta),
    CONSTRAINT ck_cuenta_saldo_no_negativo     CHECK (saldo_contable >= 0),
    CONSTRAINT ck_cuenta_retencion_no_negativa CHECK (retencion >= 0),
    CONSTRAINT ck_cuenta_tipo                  CHECK (tipo_cuenta IN ('AHORROS', 'CORRIENTE')),
    CONSTRAINT ck_cuenta_estado                CHECK (estado IN ('ACTIVA', 'BLOQUEADA', 'CERRADA'))
);

CREATE INDEX idx_cuenta_cliente ON cuenta (cliente_id);

-- ---------------------------------------------------------------------
-- transaccion: movimiento de dinero sobre una cuenta
-- (depósito HU-10, retiro HU-11, transferencia HU-09a/b/c, reverso HU-12).
-- Es también la fuente del historial de movimientos (HU-13) y del
-- detalle de una transacción (HU-14).
-- Una transferencia se guarda como dos filas con el mismo
-- transferencia_id: una SALIENTE en la cuenta origen y una ENTRANTE
-- en la cuenta destino.
-- Un reverso apunta a su transacción original, y la original REVERSADA
-- apunta a su reverso (transaccion_relacionada_id, V6). Es UNIQUE: una
-- transacción solo se reversa una vez.
-- usuario_id no tiene llave foránea: la tabla usuario es de auth-api.
-- Es NULL en las transacciones anteriores a V6.
-- ---------------------------------------------------------------------
CREATE TABLE transaccion (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cuenta_id                  UUID          NOT NULL REFERENCES cuenta (id),
    tipo                       VARCHAR(20)   NOT NULL,
    monto                      NUMERIC(15,2) NOT NULL,
    saldo_anterior             NUMERIC(15,2) NOT NULL,
    saldo_nuevo                NUMERIC(15,2) NOT NULL,
    origen                     VARCHAR(20),
    estado                     VARCHAR(20)   NOT NULL DEFAULT 'COMPLETADA',
    fecha_hora                 TIMESTAMP     NOT NULL DEFAULT now(),
    transferencia_id           UUID,
    sentido                    VARCHAR(10),
    descripcion                VARCHAR(255),
    usuario_id                 UUID,
    motivo                     VARCHAR(255),
    transaccion_relacionada_id UUID REFERENCES transaccion (id),
    CONSTRAINT uk_transaccion_relacionada UNIQUE (transaccion_relacionada_id),
    CONSTRAINT ck_transaccion_monto_positivo CHECK (monto > 0),
    CONSTRAINT ck_transaccion_tipo    CHECK (tipo IN ('DEPOSITO', 'RETIRO', 'TRANSFERENCIA', 'REVERSO')),
    CONSTRAINT ck_transaccion_origen  CHECK (origen IN ('EFECTIVO', 'CHEQUE', 'OTRO')),
    CONSTRAINT ck_transaccion_estado  CHECK (estado IN ('COMPLETADA', 'REVERSADA')),
    CONSTRAINT ck_transaccion_sentido CHECK (sentido IN ('SALIENTE', 'ENTRANTE')),
    -- TRANSFERENCIA: transferencia_id y sentido obligatorios.
    -- REVERSO: sentido obligatorio, transferencia_id opcional.
    -- DEPOSITO y RETIRO: ambos NULL.
    CONSTRAINT ck_transaccion_transferencia CHECK (
        (tipo = 'TRANSFERENCIA' AND transferencia_id IS NOT NULL AND sentido IS NOT NULL)
        OR (tipo = 'REVERSO' AND sentido IS NOT NULL)
        OR (tipo IN ('DEPOSITO', 'RETIRO') AND transferencia_id IS NULL AND sentido IS NULL)
    ),
    -- El REVERSO exige original, usuario y motivo de mínimo 10 caracteres.
    -- El motivo solo existe en los reversos.
    CONSTRAINT ck_transaccion_reverso CHECK (
        (tipo = 'REVERSO'
            AND transaccion_relacionada_id IS NOT NULL
            AND usuario_id IS NOT NULL
            AND motivo IS NOT NULL
            AND char_length(btrim(motivo)) >= 10)
        OR (tipo <> 'REVERSO' AND motivo IS NULL)
    ),
    -- Un REVERSO nunca pasa a REVERSADA, y una REVERSADA siempre apunta a su reverso.
    CONSTRAINT ck_transaccion_reversada CHECK (
        estado = 'COMPLETADA'
        OR (estado = 'REVERSADA' AND tipo <> 'REVERSO' AND transaccion_relacionada_id IS NOT NULL)
    )
);

CREATE INDEX idx_transaccion_cuenta        ON transaccion (cuenta_id);
CREATE INDEX idx_transaccion_transferencia ON transaccion (transferencia_id);

-- ---------------------------------------------------------------------
-- movimiento_administrativo_cuenta: auditoría de bloqueo, desbloqueo
-- y cierre de cuentas hechos por un ADMIN (HU-07a, HU-07b).
-- usuario_id no tiene llave foránea a propósito (igual que en V4):
-- el registro de auditoría se conserva aunque el usuario cambie.
-- ---------------------------------------------------------------------
CREATE TABLE movimiento_administrativo_cuenta (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cuenta_id       UUID         NOT NULL REFERENCES cuenta (id),
    usuario_id      UUID         NOT NULL,
    tipo_accion     VARCHAR(15)  NOT NULL,
    estado_anterior VARCHAR(20)  NOT NULL,
    estado_nuevo    VARCHAR(20)  NOT NULL,
    motivo          VARCHAR(255) NOT NULL,
    fecha_hora      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_mov_admin_tipo_accion     CHECK (tipo_accion IN ('BLOQUEO', 'DESBLOQUEO', 'CIERRE')),
    CONSTRAINT ck_mov_admin_estado_anterior CHECK (estado_anterior IN ('ACTIVA', 'BLOQUEADA', 'CERRADA')),
    CONSTRAINT ck_mov_admin_estado_nuevo    CHECK (estado_nuevo IN ('ACTIVA', 'BLOQUEADA', 'CERRADA')),
    CONSTRAINT ck_mov_admin_motivo          CHECK (char_length(btrim(motivo)) >= 10)
);

CREATE INDEX idx_mov_admin_cuenta  ON movimiento_administrativo_cuenta (cuenta_id);
CREATE INDEX idx_mov_admin_usuario ON movimiento_administrativo_cuenta (usuario_id);

-- ---------------------------------------------------------------------
-- auditoria_datos_cliente: cambios de correo y teléfono de un cliente
-- (HU-03, V6). usuario_id no es FK: la tabla usuario es de auth-api.
-- ---------------------------------------------------------------------
CREATE TABLE auditoria_datos_cliente (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id      UUID         NOT NULL REFERENCES cliente (id),
    usuario_id      UUID         NOT NULL,
    campo           VARCHAR(20)  NOT NULL,
    valor_anterior  VARCHAR(255) NOT NULL,
    valor_nuevo     VARCHAR(255) NOT NULL,
    fecha_hora      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_aud_datos_cliente_campo CHECK (campo IN ('EMAIL', 'TELEFONO'))
);

CREATE INDEX idx_aud_datos_cliente_cliente ON auditoria_datos_cliente (cliente_id);
CREATE INDEX idx_aud_datos_cliente_usuario ON auditoria_datos_cliente (usuario_id);

-- ---------------------------------------------------------------------
-- movimiento_administrativo_cliente: auditoría de la inactivación de un
-- cliente (HU-04, V6). No mueve dinero. No hay reactivación.
-- ---------------------------------------------------------------------
CREATE TABLE movimiento_administrativo_cliente (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id       UUID         NOT NULL REFERENCES cliente (id),
    usuario_id       UUID         NOT NULL,
    estado_anterior  VARCHAR(20)  NOT NULL,
    estado_nuevo     VARCHAR(20)  NOT NULL,
    motivo           VARCHAR(255) NOT NULL,
    fecha_hora       TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_mov_admin_cliente_estado_anterior CHECK (estado_anterior = 'ACTIVO'),
    CONSTRAINT ck_mov_admin_cliente_estado_nuevo    CHECK (estado_nuevo = 'INACTIVO'),
    CONSTRAINT ck_mov_admin_cliente_motivo          CHECK (char_length(btrim(motivo)) >= 10)
);

CREATE INDEX idx_mov_admin_cliente_cliente ON movimiento_administrativo_cliente (cliente_id);
CREATE INDEX idx_mov_admin_cliente_usuario ON movimiento_administrativo_cliente (usuario_id);

-- ---------------------------------------------------------------------
-- Triggers de protección (V7)
-- ---------------------------------------------------------------------
-- 1. Las tablas de auditoría no se editan ni se borran.
CREATE FUNCTION fn_bloquear_cambios_auditoria() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La tabla % es de auditoría: no se permite % de filas', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_aud_datos_cliente_inmutable
    BEFORE UPDATE OR DELETE ON auditoria_datos_cliente
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_cambios_auditoria();

CREATE TRIGGER trg_mov_admin_cliente_inmutable
    BEFORE UPDATE OR DELETE ON movimiento_administrativo_cliente
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_cambios_auditoria();

CREATE TRIGGER trg_mov_admin_cuenta_inmutable
    BEFORE UPDATE OR DELETE ON movimiento_administrativo_cuenta
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_cambios_auditoria();

-- 2. El historial de transacciones no se borra 
CREATE FUNCTION fn_bloquear_borrado_transaccion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Las transacciones no se borran: el historial se conserva';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transaccion_no_borrar
    BEFORE DELETE ON transaccion
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado_transaccion();

-- 3. Una transacción solo puede pasar de COMPLETADA a REVERSADA, y solo en ese
--    momento se enlaza a su reverso (que debe ser un REVERSO que la apunte a ella).
CREATE FUNCTION fn_proteger_transaccion() RETURNS trigger AS $$
BEGIN
    IF NEW.cuenta_id          IS DISTINCT FROM OLD.cuenta_id
    OR NEW.tipo               IS DISTINCT FROM OLD.tipo
    OR NEW.monto              IS DISTINCT FROM OLD.monto
    OR NEW.saldo_anterior     IS DISTINCT FROM OLD.saldo_anterior
    OR NEW.saldo_nuevo        IS DISTINCT FROM OLD.saldo_nuevo
    OR NEW.origen             IS DISTINCT FROM OLD.origen
    OR NEW.fecha_hora         IS DISTINCT FROM OLD.fecha_hora
    OR NEW.transferencia_id   IS DISTINCT FROM OLD.transferencia_id
    OR NEW.sentido            IS DISTINCT FROM OLD.sentido
    OR NEW.descripcion        IS DISTINCT FROM OLD.descripcion
    OR NEW.usuario_id         IS DISTINCT FROM OLD.usuario_id
    OR NEW.motivo             IS DISTINCT FROM OLD.motivo THEN
        RAISE EXCEPTION 'Una transacción no se modifica: solo puede pasar a REVERSADA';
    END IF;

    IF NEW.estado IS DISTINCT FROM OLD.estado
        AND NOT (OLD.estado = 'COMPLETADA' AND NEW.estado = 'REVERSADA') THEN
        RAISE EXCEPTION 'Cambio de estado no permitido: % a %', OLD.estado, NEW.estado;
    END IF;

    -- El enlace solo se asigna una vez, al pasar a REVERSADA.
    IF NEW.transaccion_relacionada_id IS DISTINCT FROM OLD.transaccion_relacionada_id
        AND NOT (OLD.estado = 'COMPLETADA' AND NEW.estado = 'REVERSADA'
                AND OLD.transaccion_relacionada_id IS NULL) THEN
        RAISE EXCEPTION 'El enlace con el reverso solo se asigna al pasar a REVERSADA';
    END IF;

    -- Al pasar a REVERSADA, el enlace debe apuntar al REVERSO que anula esta transacción.
    IF OLD.estado = 'COMPLETADA' AND NEW.estado = 'REVERSADA'
        AND NOT EXISTS (
            SELECT 1 FROM transaccion r
            WHERE r.id = NEW.transaccion_relacionada_id
                AND r.tipo = 'REVERSO'
                AND r.transaccion_relacionada_id = NEW.id) THEN
        RAISE EXCEPTION 'La transacción solo puede pasar a REVERSADA enlazada a su REVERSO';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transaccion_proteger
    BEFORE UPDATE ON transaccion
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_transaccion();

-- 4. Un cliente INACTIVO no recibe cuentas nuevas (HU-04).
CREATE FUNCTION fn_validar_cliente_activo_cuenta() RETURNS trigger AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM cliente WHERE id = NEW.cliente_id AND estado = 'INACTIVO') THEN
        RAISE EXCEPTION 'El cliente está inactivo: no puede tener cuentas nuevas';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cuenta_cliente_activo
    BEFORE INSERT ON cuenta
    FOR EACH ROW EXECUTE FUNCTION fn_validar_cliente_activo_cuenta();

COMMIT;