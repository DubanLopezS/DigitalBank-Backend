-- Garantiza la relación 1:1 entre cliente y usuario.
-- NOTA: UNIQUE en Postgres permite múltiples NULL, así que los usuarios 
-- ADMIN/CAJERO (sin cliente asociado) no entran en conflicto entre sí.
ALTER TABLE usuario
    ADD CONSTRAINT uk_usuario_cliente UNIQUE (cliente_id);


-- Valores permitidos para los campos enumerados.
-- Se usa VARCHAR + CHECK en vez de ENUM nativo de Postgres para poder
-- agregar valores nuevos en migraciones futuras sin ALTER TYPE.

ALTER TABLE cliente
    ADD CONSTRAINT ck_cliente_estado CHECK (estado IN ('ACTIVO','INACTIVO'));

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_rol CHECK (rol IN ('CLIENTE','ADMIN','CAJERO')),
    ADD CONSTRAINT ck_usuario_estado_seg CHECK (estado_seguridad IN ('ACTIVO','BLOQUEADO_TEMPORAL'));

ALTER TABLE cuenta
    ADD CONSTRAINT ck_cuenta_tipo CHECK (tipo_cuenta IN ('AHORROS','CORRIENTE')),
    ADD CONSTRAINT ck_cuenta_estado CHECK (estado IN ('ACTIVA','BLOQUEADA','CERRADA')),
    ADD CONSTRAINT ck_cuenta_retencion_no_negativa CHECK (retencion >= 0);

-- Sprint 1 solo contempla depósitos completados (HU10, AC13).
-- Si más adelante aparece una HU de reversión o de transacciones fallidas,
-- se amplían estos valores en una migración nueva.
ALTER TABLE transaccion
    ADD CONSTRAINT ck_transaccion_tipo CHECK (tipo IN ('DEPOSITO')),
    ADD CONSTRAINT ck_transaccion_origen CHECK (origen IN ('EFECTIVO','CHEQUE','OTRO')),
    ADD CONSTRAINT ck_transaccion_estado CHECK (estado IN ('COMPLETADA'));