-- Copia de las tablas cuenta y transaccion de las migraciones V1 a V3 de core-api.
-- Se omite la FK a cliente porque las consultas de reportes no la usan.
DROP TABLE IF EXISTS transaccion;
DROP TABLE IF EXISTS cuenta;

CREATE TABLE cuenta (
    id                UUID PRIMARY KEY,
    cliente_id        UUID NOT NULL,
    numero_cuenta     VARCHAR(10) NOT NULL,
    tipo_cuenta       VARCHAR(20) NOT NULL,
    saldo_contable    NUMERIC(15,2) NOT NULL DEFAULT 0,
    retencion         NUMERIC(15,2) NOT NULL DEFAULT 0,
    estado            VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    fecha_apertura    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cuenta_numero UNIQUE (numero_cuenta)
);

CREATE TABLE transaccion (
    id                UUID PRIMARY KEY,
    cuenta_id         UUID NOT NULL REFERENCES cuenta(id),
    tipo              VARCHAR(20) NOT NULL,
    monto             NUMERIC(15,2) NOT NULL,
    saldo_anterior    NUMERIC(15,2) NOT NULL,
    saldo_nuevo       NUMERIC(15,2) NOT NULL,
    origen            VARCHAR(20),
    estado            VARCHAR(20) NOT NULL DEFAULT 'COMPLETADA',
    fecha_hora        TIMESTAMP NOT NULL,
    transferencia_id  UUID,
    sentido           VARCHAR(10),
    descripcion       VARCHAR(255),
    CONSTRAINT ck_transaccion_tipo CHECK (tipo IN ('DEPOSITO','RETIRO','TRANSFERENCIA')),
    CONSTRAINT ck_transaccion_origen CHECK (origen IN ('EFECTIVO','CHEQUE','OTRO')),
    CONSTRAINT ck_transaccion_estado CHECK (estado IN ('COMPLETADA')),
    CONSTRAINT ck_transaccion_sentido CHECK (sentido IN ('SALIENTE','ENTRANTE')),
    CONSTRAINT ck_transaccion_transferencia CHECK ((tipo = 'TRANSFERENCIA' AND transferencia_id IS NOT NULL AND sentido IS NOT NULL) OR (tipo <> 'TRANSFERENCIA' AND transferencia_id IS NULL AND sentido IS NULL))
);
