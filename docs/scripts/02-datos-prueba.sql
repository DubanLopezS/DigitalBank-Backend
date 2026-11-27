-- =====================================================================
-- Digital Bank - Datos de prueba de 05-prueba-permisos.sql
-- Responsable: Samuel Velásquez
--
-- Son los datos que usa 05-prueba-permisos.sql para probar los roles
-- de 04-roles-seguridad.sql. Se corre después de
-- 01-despliegue-estructuras.sql, en una base de pruebas (nunca en la
-- de Supabase que usa la aplicación).
-- Los IDs son fijos para que 05-prueba-permisos.sql los pueda usar
-- directamente, sin variables.
--
--   Ana Gómez    cliente 11111111-...  cuentas 1000000001 (ahorros, $500.000)
--                                              1000000002 (corriente, $0)
--   Bruno Ríos   cliente 22222222-...  cuentas 2000000001 (ahorros, $100.000)
--                                              2000000002 (ahorros, CERRADA)
--   Usuario ADMIN interno               99999999-...
-- =====================================================================

BEGIN;

INSERT INTO cliente (id, tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento, telefono, email)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'CC', '1001001001', 'Ana',   'Gómez', '1998-03-14', '3001112233', 'ana.gomez@correo.test'),
    ('22222222-2222-2222-2222-222222222222', 'CC', '1002002002', 'Bruno', 'Ríos',  '1995-11-02', '3004445566', 'bruno.rios@correo.test');

-- password_hash es un valor de relleno: estas consultas no prueban el login.
INSERT INTO usuario (id, cliente_id, email, password_hash, rol)
VALUES
    ('11111111-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'ana.gomez@correo.test',  'hash-de-prueba', 'CLIENTE'),
    ('22222222-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'bruno.rios@correo.test', 'hash-de-prueba', 'CLIENTE'),
    ('99999999-9999-9999-9999-999999999999', NULL,                                   'admin@correo.test',      'hash-de-prueba', 'ADMIN');

INSERT INTO cuenta (id, cliente_id, numero_cuenta, tipo_cuenta, saldo_contable, retencion, estado)
VALUES
    ('a1000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '1000000001', 'AHORROS',   500000.00, 0, 'ACTIVA'),
    ('a1000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', '1000000002', 'CORRIENTE',      0.00, 0, 'ACTIVA'),
    ('b2000000-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', '2000000001', 'AHORROS',   100000.00, 0, 'ACTIVA'),
    ('b2000000-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', '2000000002', 'AHORROS',        0.00, 0, 'CERRADA');

-- Depósitos iniciales (HU-10), para que el historial tenga de dónde salir
-- y los saldos cuadren con las transacciones.
INSERT INTO transaccion (cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo, origen, fecha_hora)
VALUES
    ('a1000000-0000-0000-0000-000000000001', 'DEPOSITO', 500000.00, 0, 500000.00, 'EFECTIVO', '2026-10-01 09:00'),
    ('b2000000-0000-0000-0000-000000000001', 'DEPOSITO', 100000.00, 0, 100000.00, 'CHEQUE',   '2026-10-01 10:00');

COMMIT;
