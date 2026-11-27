-- =====================================================================
-- Digital Bank - Prueba de los roles de 04-roles-seguridad.sql
-- Responsable: Samuel Velásquez
--
-- Se corre con psql, como superusuario, en una base de pruebas que ya
-- tiene 01, 02 y 04 aplicados:
--   psql "<conexión>" -v ON_ERROR_STOP=1 -f 05-prueba-permisos.sql
--
-- Para cada rol se hace SET ROLE y se ejecuta:
--   - lo que la API sí hace (en una transacción que se deshace al final,
--     para no cambiar los datos de prueba): si algo falla, psql se
--     detiene y muestra el error. Cada UPDATE y cada SELECT ... FOR UPDATE
--     pasa por debe_afectar, que exige el número exacto de filas que sale
--     de los datos de 02: con RLS, a un rol sin política no le sale
--     error, simplemente no ve la fila y la sentencia afecta cero filas;
--   - lo que no debe poder hacer, dentro de bloques DO que esperan el
--     error de permisos: muestran "denegado" si la base lo rechaza y
--     detienen el script con "FALLO" si lo deja pasar.
-- =====================================================================

-- Bloque reutilizable: ejecuta una sentencia y exige que falle por permisos.
CREATE OR REPLACE FUNCTION pg_temp.debe_fallar(etiqueta text, sentencia text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    EXECUTE sentencia;
    RAISE EXCEPTION 'FALLO: % se permitió', etiqueta;
EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'denegado: %', etiqueta;
END $$;

-- Bloque reutilizable: ejecuta una sentencia y exige que afecte o devuelva
-- exactamente las filas esperadas. Sirve para detectar una política RLS
-- que falta, que no da error sino cero filas.
CREATE OR REPLACE FUNCTION pg_temp.debe_afectar(etiqueta text, sentencia text, filas_esperadas int)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    filas int;
BEGIN
    EXECUTE sentencia;
    GET DIAGNOSTICS filas = ROW_COUNT;
    IF filas <> filas_esperadas THEN
        RAISE EXCEPTION 'FALLO: % afectó % filas y se esperaban %', etiqueta, filas, filas_esperadas;
    END IF;
    RAISE NOTICE 'ok: %', etiqueta;
END $$;


-- =====================================================================
-- digitalbank_auth (auth-api)
-- =====================================================================
SET ROLE digitalbank_auth;

BEGIN;
-- HU-01: valida duplicados y registra cliente + usuario en una transacción
SELECT count(*) > 0 AS documento_existe FROM cliente
WHERE tipo_documento = 'CC' AND numero_documento = '1003003003';
SELECT count(*) > 0 AS email_existe FROM cliente WHERE email = 'carla.mesa@correo.test';
INSERT INTO cliente (id, tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento, email)
VALUES ('33333333-3333-3333-3333-333333333333', 'CC', '1003003003', 'Carla', 'Mesa', '2000-01-20',
        'carla.mesa@correo.test');
INSERT INTO usuario (id, cliente_id, email, password_hash, rol)
VALUES ('33333333-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333',
        'carla.mesa@correo.test', 'hash-de-prueba', 'CLIENTE');
-- HU-18: login, intento fallido y login exitoso
SELECT id, rol, estado_seguridad, intentos_fallidos FROM usuario WHERE email = 'ana.gomez@correo.test';
SELECT pg_temp.debe_afectar('auth registra un intento fallido',
    'UPDATE usuario SET intentos_fallidos = intentos_fallidos + 1 WHERE email = ''ana.gomez@correo.test''', 1);
-- HU-18 con HU-04: antes de dar el token, AuthServiceImpl lee el estado del
-- cliente del usuario para rechazar a un cliente INACTIVO.
SELECT estado FROM cliente WHERE id = '11111111-1111-1111-1111-111111111111';
SELECT pg_temp.debe_afectar('auth registra un login exitoso',
    'UPDATE usuario SET intentos_fallidos = 0, ultimo_login = now() WHERE email = ''ana.gomez@correo.test''', 1);
-- HU-02: cuentas del cliente, sin saldos, con la consulta de
-- CuentasDelClienteJdbcRepository. Ana tiene dos cuentas en 02.
SELECT pg_temp.debe_afectar('auth lista las cuentas del cliente',
    'SELECT numero_cuenta, tipo_cuenta, estado '
    'FROM cuenta '
    'WHERE cliente_id = ''11111111-1111-1111-1111-111111111111'' '
    'ORDER BY numero_cuenta', 2);
-- HU-03: cambio de correo y teléfono. ClienteServiceImpl revisa que el
-- correo esté libre, guarda el cliente con solo las columnas que cambian,
-- cambia el correo del usuario para que el login use el nuevo y deja una
-- fila de auditoría por cada dato.
SELECT count(*) > 0 AS email_ocupado FROM cliente WHERE email = 'ana.gomez.nuevo@correo.test';
SELECT cliente_id FROM usuario WHERE email = 'ana.gomez.nuevo@correo.test';
SELECT pg_temp.debe_afectar('auth cambia correo y teléfono del cliente',
    'UPDATE cliente SET email = ''ana.gomez.nuevo@correo.test'', telefono = ''3009998877'' '
    'WHERE id = ''11111111-1111-1111-1111-111111111111''', 1);
SELECT pg_temp.debe_afectar('auth cambia el correo del usuario',
    'UPDATE usuario SET email = ''ana.gomez.nuevo@correo.test'' '
    'WHERE id = ''11111111-0000-0000-0000-000000000001''', 1);
INSERT INTO auditoria_datos_cliente (id, cliente_id, usuario_id, campo, valor_anterior, valor_nuevo, fecha_hora)
VALUES (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '11111111-0000-0000-0000-000000000001',
        'EMAIL', 'ana.gomez@correo.test', 'ana.gomez.nuevo@correo.test', now()),
       (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '11111111-0000-0000-0000-000000000001',
        'TELEFONO', '3001112233', '3009998877', now());
ROLLBACK;

SELECT pg_temp.debe_fallar('auth lee el saldo de las cuentas', 'SELECT saldo_contable FROM cuenta');
SELECT pg_temp.debe_fallar('auth lee transacciones',  'SELECT monto FROM transaccion');
SELECT pg_temp.debe_fallar('auth borra usuarios',     'DELETE FROM usuario');
SELECT pg_temp.debe_fallar('auth modifica el estado del cliente', 'UPDATE cliente SET estado = ''INACTIVO''');
SELECT pg_temp.debe_fallar('auth modifica cuentas',   'UPDATE cuenta SET estado = ''CERRADA''');
SELECT pg_temp.debe_fallar('auth modifica la auditoría de datos del cliente',
    'UPDATE auditoria_datos_cliente SET valor_nuevo = ''borrado de rastro''');
SELECT pg_temp.debe_fallar('auth borra la auditoría de datos del cliente', 'DELETE FROM auditoria_datos_cliente');

RESET ROLE;


-- =====================================================================
-- digitalbank_core (core-api)
-- =====================================================================
SET ROLE digitalbank_core;

BEGIN;
-- HU-05: busca el cliente por documento y abre la cuenta
SELECT id, estado FROM cliente WHERE tipo_documento = 'CC' AND numero_documento = '1001001001';
SELECT count(*) > 0 AS numero_ocupado FROM cuenta WHERE numero_cuenta = '1000000009';
INSERT INTO cuenta (id, cliente_id, numero_cuenta, tipo_cuenta, saldo_contable, retencion, estado, fecha_apertura)
VALUES ('a1000000-0000-0000-0000-000000000009', '11111111-1111-1111-1111-111111111111',
        '1000000009', 'AHORROS', 0, 0, 'ACTIVA', now());
-- HU-10 / HU-11 / HU-09: bloqueo de fila, cambio de saldo y registro
SELECT pg_temp.debe_afectar('core bloquea la cuenta para mover saldo',
    'SELECT saldo_contable, retencion, estado FROM cuenta '
    'WHERE id = ''a1000000-0000-0000-0000-000000000001'' FOR UPDATE', 1);
SELECT pg_temp.debe_afectar('core cambia el saldo de la cuenta',
    'UPDATE cuenta SET saldo_contable = saldo_contable - 1000 '
    'WHERE id = ''a1000000-0000-0000-0000-000000000001''', 1);
-- HU-14: cada depósito, retiro y transferencia guarda el usuario que la originó.
INSERT INTO transaccion (id, cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo, estado, fecha_hora, usuario_id)
VALUES (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', 'RETIRO', 1000, 500000, 499000,
        'COMPLETADA', now(), '99999999-9999-9999-9999-999999999999');
-- HU-07a / HU-07b: cambio de estado y auditoría
SELECT pg_temp.debe_afectar('core bloquea una cuenta',
    'UPDATE cuenta SET estado = ''BLOQUEADA'' WHERE id = ''b2000000-0000-0000-0000-000000000001''', 1);
INSERT INTO movimiento_administrativo_cuenta
    (id, cuenta_id, usuario_id, tipo_accion, estado_anterior, estado_nuevo, motivo, fecha_hora)
VALUES (gen_random_uuid(), 'b2000000-0000-0000-0000-000000000001', '99999999-9999-9999-9999-999999999999',
        'BLOQUEO', 'ACTIVA', 'BLOQUEADA', 'Prueba de permisos del rol', now());
ROLLBACK;

BEGIN;
-- HU-04: inactivación de Bruno, que en 02 está ACTIVO, con una cuenta
-- ACTIVA y otra CERRADA. Se bloquea primero la fila del cliente y después
-- sus cuentas; solo la ACTIVA pasa a BLOQUEADA. La regla de saldo $0 la
-- revisa core-api en Java antes de estos pasos; aquí solo se prueba que
-- el rol pueda hacer cada uno, por eso no importa que la cuenta tenga saldo.
SELECT pg_temp.debe_afectar('core bloquea la fila del cliente',
    'SELECT estado FROM cliente WHERE id = ''22222222-2222-2222-2222-222222222222'' FOR UPDATE', 1);
SELECT id FROM cuenta WHERE cliente_id = '22222222-2222-2222-2222-222222222222';
SELECT pg_temp.debe_afectar('core bloquea las cuentas del cliente',
    'SELECT id, saldo_contable, retencion, estado FROM cuenta '
    'WHERE cliente_id = ''22222222-2222-2222-2222-222222222222'' ORDER BY id FOR UPDATE', 2);
SELECT pg_temp.debe_afectar('core pasa a BLOQUEADA la cuenta activa del cliente',
    'UPDATE cuenta SET estado = ''BLOQUEADA'' '
    'WHERE cliente_id = ''22222222-2222-2222-2222-222222222222'' AND estado = ''ACTIVA''', 1);
INSERT INTO movimiento_administrativo_cuenta
    (id, cuenta_id, usuario_id, tipo_accion, estado_anterior, estado_nuevo, motivo, fecha_hora)
VALUES (gen_random_uuid(), 'b2000000-0000-0000-0000-000000000001', '99999999-9999-9999-9999-999999999999',
        'BLOQUEO', 'ACTIVA', 'BLOQUEADA', 'Inactivación del cliente titular', now());
SELECT pg_temp.debe_afectar('core inactiva al cliente',
    'UPDATE cliente SET estado = ''INACTIVO'' WHERE id = ''22222222-2222-2222-2222-222222222222''', 1);
INSERT INTO movimiento_administrativo_cliente
    (id, cliente_id, usuario_id, estado_anterior, estado_nuevo, motivo, fecha_hora)
VALUES (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', '99999999-9999-9999-9999-999999999999',
        'ACTIVO', 'INACTIVO', 'Inactivación del cliente titular', now());
ROLLBACK;

BEGIN;
-- HU-12: reverso de un depósito. Se registra un depósito con id fijo en la
-- cuenta de ahorros de Ana, que en 02 tiene $500.000; ReversoServiceImpl
-- lo carga con FOR UPDATE, debita la cuenta, inserta el REVERSO apuntando
-- al depósito y marca el depósito REVERSADA apuntando al reverso. Es el
-- único cambio que deja el trigger fn_proteger_transaccion de V7.
SELECT pg_temp.debe_afectar('core acredita el depósito',
    'UPDATE cuenta SET saldo_contable = saldo_contable + 20000 '
    'WHERE id = ''a1000000-0000-0000-0000-000000000001''', 1);
INSERT INTO transaccion (id, cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo, origen, estado, fecha_hora, usuario_id)
VALUES ('c3000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'DEPOSITO',
        20000, 500000, 520000, 'EFECTIVO', 'COMPLETADA', now(), '99999999-9999-9999-9999-999999999999');
SELECT tipo, transferencia_id FROM transaccion WHERE id = 'c3000000-0000-0000-0000-000000000001';
SELECT pg_temp.debe_afectar('core bloquea la transacción original',
    'SELECT * FROM transaccion WHERE id = ''c3000000-0000-0000-0000-000000000001'' FOR UPDATE', 1);
SELECT pg_temp.debe_afectar('core bloquea la cuenta del reverso',
    'SELECT saldo_contable, retencion, estado FROM cuenta '
    'WHERE id = ''a1000000-0000-0000-0000-000000000001'' FOR UPDATE', 1);
SELECT pg_temp.debe_afectar('core debita el reverso',
    'UPDATE cuenta SET saldo_contable = saldo_contable - 20000 '
    'WHERE id = ''a1000000-0000-0000-0000-000000000001''', 1);
-- El reverso de un depósito sale de la cuenta, así que va SALIENTE.
INSERT INTO transaccion (id, cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo, estado, fecha_hora,
                         sentido, usuario_id, motivo, transaccion_relacionada_id)
VALUES ('c3000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', 'REVERSO',
        20000, 520000, 500000, 'COMPLETADA', now(), 'SALIENTE', '99999999-9999-9999-9999-999999999999',
        'Depósito registrado dos veces en caja', 'c3000000-0000-0000-0000-000000000001');
SELECT pg_temp.debe_afectar('core marca REVERSADA la transacción original',
    'UPDATE transaccion SET estado = ''REVERSADA'', '
    'transaccion_relacionada_id = ''c3000000-0000-0000-0000-000000000002'' '
    'WHERE id = ''c3000000-0000-0000-0000-000000000001''', 1);
ROLLBACK;

SELECT pg_temp.debe_fallar('core lee datos personales del cliente', 'SELECT nombres, email, telefono FROM cliente');
SELECT pg_temp.debe_fallar('core lee credenciales',          'SELECT password_hash FROM usuario');
SELECT pg_temp.debe_fallar('core registra clientes',
    'INSERT INTO cliente (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento, email) '
    'VALUES (''CC'', ''1'', ''x'', ''x'', ''2000-01-01'', ''x@x.test'')');
SELECT pg_temp.debe_fallar('core modifica el correo del cliente',   'UPDATE cliente SET email = ''otro@correo.test''');
SELECT pg_temp.debe_fallar('core modifica el teléfono del cliente', 'UPDATE cliente SET telefono = ''3000000000''');
SELECT pg_temp.debe_fallar('core lee la auditoría de datos del cliente', 'SELECT * FROM auditoria_datos_cliente');
SELECT pg_temp.debe_fallar('core modifica el monto de una transacción', 'UPDATE transaccion SET monto = 1');
SELECT pg_temp.debe_fallar('core borra transacciones',       'DELETE FROM transaccion');
SELECT pg_temp.debe_fallar('core modifica la auditoría',     'UPDATE movimiento_administrativo_cuenta SET motivo = ''borrado de rastro''');
SELECT pg_temp.debe_fallar('core modifica la auditoría del cliente',
    'UPDATE movimiento_administrativo_cliente SET motivo = ''borrado de rastro''');
SELECT pg_temp.debe_fallar('core borra la auditoría del cliente', 'DELETE FROM movimiento_administrativo_cliente');
SELECT pg_temp.debe_fallar('core borra cuentas',             'DELETE FROM cuenta');

RESET ROLE;


-- =====================================================================
-- digitalbank_reportes (reportes-api)
-- =====================================================================
SET ROLE digitalbank_reportes;

-- HU-13: titular de la cuenta, página de movimientos y total
SELECT cliente_id FROM cuenta WHERE id = 'a1000000-0000-0000-0000-000000000001';
SELECT t.id, t.tipo, t.monto, t.fecha_hora, t.saldo_anterior, t.saldo_nuevo, t.estado,
       t.origen, t.sentido, t.descripcion, t.transferencia_id,
       c.numero_cuenta AS numero_cuenta_relacionada
FROM transaccion t
LEFT JOIN transaccion otra ON otra.transferencia_id = t.transferencia_id
       AND otra.id <> t.id AND otra.tipo = t.tipo
LEFT JOIN cuenta c ON c.id = otra.cuenta_id
WHERE t.cuenta_id = 'a1000000-0000-0000-0000-000000000001'
ORDER BY t.fecha_hora DESC, t.id
LIMIT 20 OFFSET 0;
SELECT count(*) FROM transaccion t WHERE t.cuenta_id = 'a1000000-0000-0000-0000-000000000001';
-- HU-14: detalle de una transacción, con la consulta de TransaccionJdbcRepository.
-- Las transacciones de 02 no tienen id fijo, así que se toma el depósito
-- inicial de la cuenta de ahorros de Ana.
SELECT t.id, t.tipo, t.monto, t.estado, t.fecha_hora, t.origen, t.sentido, t.descripcion,
       t.usuario_id, t.transaccion_relacionada_id,
       c.numero_cuenta, c.cliente_id,
       oc.numero_cuenta AS otra_numero_cuenta, oc.cliente_id AS otra_cliente_id
FROM transaccion t
JOIN cuenta c ON c.id = t.cuenta_id
LEFT JOIN transaccion otra ON t.tipo = 'TRANSFERENCIA' AND otra.tipo = 'TRANSFERENCIA'
     AND otra.transferencia_id = t.transferencia_id AND otra.id <> t.id
LEFT JOIN cuenta oc ON oc.id = otra.cuenta_id
WHERE t.id = (SELECT id FROM transaccion
              WHERE cuenta_id = 'a1000000-0000-0000-0000-000000000001' AND tipo = 'DEPOSITO');

SELECT pg_temp.debe_fallar('reportes lee saldos de cuenta', 'SELECT saldo_contable FROM cuenta');
SELECT pg_temp.debe_fallar('reportes lee clientes',         'SELECT nombres FROM cliente');
SELECT pg_temp.debe_fallar('reportes lee credenciales',     'SELECT password_hash FROM usuario');
SELECT pg_temp.debe_fallar('reportes lee la auditoría de datos del cliente', 'SELECT * FROM auditoria_datos_cliente');
SELECT pg_temp.debe_fallar('reportes lee la auditoría del cliente', 'SELECT * FROM movimiento_administrativo_cliente');
SELECT pg_temp.debe_fallar('reportes escribe transacciones',
    'INSERT INTO transaccion (cuenta_id, tipo, monto, saldo_anterior, saldo_nuevo) '
    'VALUES (''a1000000-0000-0000-0000-000000000001'', ''DEPOSITO'', 1, 0, 1)');
SELECT pg_temp.debe_fallar('reportes modifica cuentas',     'UPDATE cuenta SET estado = ''CERRADA''');

RESET ROLE;


-- =====================================================================
-- anon y authenticated (solo si existen, es decir, en Supabase)
-- =====================================================================
SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') AS hay_anon \gset
\if :hay_anon
SET ROLE anon;
SELECT pg_temp.debe_fallar('anon lee cuentas',   'SELECT * FROM cuenta');
SELECT pg_temp.debe_fallar('anon lee usuarios',  'SELECT * FROM usuario');
SELECT pg_temp.debe_fallar('anon lee la auditoría de datos del cliente', 'SELECT * FROM auditoria_datos_cliente');
SELECT pg_temp.debe_fallar('anon lee la auditoría del cliente',          'SELECT * FROM movimiento_administrativo_cliente');
RESET ROLE;
SET ROLE authenticated;
SELECT pg_temp.debe_fallar('authenticated lee cuentas',       'SELECT * FROM cuenta');
SELECT pg_temp.debe_fallar('authenticated lee transacciones', 'SELECT * FROM transaccion');
SELECT pg_temp.debe_fallar('authenticated lee la auditoría de datos del cliente', 'SELECT * FROM auditoria_datos_cliente');
SELECT pg_temp.debe_fallar('authenticated lee la auditoría del cliente',          'SELECT * FROM movimiento_administrativo_cliente');
RESET ROLE;
\endif

SELECT 'Prueba de permisos terminada sin fallos' AS resultado;
