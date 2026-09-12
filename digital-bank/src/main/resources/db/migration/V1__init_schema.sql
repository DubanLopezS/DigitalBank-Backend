CREATE TABLE cliente (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_documento    VARCHAR(20)  NOT NULL,
    numero_documento  VARCHAR(30)  NOT NULL,
    nombres           VARCHAR(100) NOT NULL,
    apellidos         VARCHAR(100) NOT NULL,
    fecha_nacimiento  DATE         NOT NULL,
    telefono          VARCHAR(20),
    email             VARCHAR(150) NOT NULL,
    estado            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
    fecha_registro    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_cliente_documento UNIQUE (tipo_documento, numero_documento),
    CONSTRAINT uk_cliente_email UNIQUE (email)
);

CREATE TABLE usuario (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id          UUID REFERENCES cliente(id),
    email               VARCHAR(150) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    rol                 VARCHAR(20)  NOT NULL DEFAULT 'CLIENTE',
    intentos_fallidos   INT          NOT NULL DEFAULT 0,
    estado_seguridad    VARCHAR(30)  NOT NULL DEFAULT 'ACTIVO',
    fecha_bloqueo       TIMESTAMP,
    ultimo_login        TIMESTAMP,
    CONSTRAINT uk_usuario_email UNIQUE (email)
);

CREATE TABLE cuenta (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id        UUID NOT NULL REFERENCES cliente(id),
    numero_cuenta     VARCHAR(10) NOT NULL,
    tipo_cuenta       VARCHAR(20) NOT NULL,
    saldo_contable    NUMERIC(15,2) NOT NULL DEFAULT 0,
    retencion         NUMERIC(15,2) NOT NULL DEFAULT 0,
    estado            VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    fecha_apertura    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_cuenta_numero UNIQUE (numero_cuenta),
    CONSTRAINT ck_cuenta_saldo_no_negativo CHECK (saldo_contable >= 0)
);

CREATE TABLE transaccion (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cuenta_id         UUID NOT NULL REFERENCES cuenta(id),
    tipo              VARCHAR(20) NOT NULL,
    monto             NUMERIC(15,2) NOT NULL,
    saldo_anterior    NUMERIC(15,2) NOT NULL,
    saldo_nuevo       NUMERIC(15,2) NOT NULL,
    origen            VARCHAR(20),
    estado            VARCHAR(20) NOT NULL DEFAULT 'COMPLETADA',
    fecha_hora        TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_transaccion_monto_positivo CHECK (monto > 0)
);

CREATE INDEX idx_cuenta_cliente ON cuenta(cliente_id);
CREATE INDEX idx_transaccion_cuenta ON transaccion(cuenta_id);