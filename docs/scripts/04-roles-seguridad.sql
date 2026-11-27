-- =====================================================================
-- Digital Bank - Roles y esquema de seguridad de la base de datos
-- Motor: PostgreSQL 15 o superior (Supabase)
-- Responsable: Samuel Velásquez
--
-- Qué resuelve:
--   R05 "Ninguna separación de privilegios en la base": hoy las tres
--   APIs se conectan con el mismo usuario y pueden hacer cualquier cosa
--   sobre cualquier tabla. Que reportes-api solo lea depende del código.
--   R13 "Supabase sin RLS": ninguna tabla tiene Row Level Security, y
--   en Supabase los roles anon y authenticated de la API REST pueden
--   tener permisos sobre las tablas de public.
--
-- Qué hace:
--   1. Crea un rol de conexión por API, cada uno con solo los permisos
--      que esa API usa en el código de main (ver tabla abajo).
--   2. Quita los permisos de anon y authenticated sobre las tablas,
--      si esos roles existen (solo existen en Supabase).
--   3. Activa RLS en las siete tablas, con políticas solo para los
--      tres roles de las APIs. El dueño de las tablas (postgres) no se
--      ve afectado, así que Flyway y crear-usuarios-internos.sql siguen
--      funcionando igual.
--   4. Termina con consultas de verificación.
--
-- Permisos por API (sacados del código). Los del Sprint 3 salen del ADR-0015:
--   digitalbank_auth      auth-api: registro de clientes (HU-01), login (HU-18),
--                         consulta (HU-02) y actualización del cliente (HU-03)
--     cliente      SELECT, INSERT              ClienteServiceImpl, AuthServiceImpl
--                  UPDATE (email, telefono)    ClienteServiceImpl.actualizarCliente (HU-03)
--     usuario      SELECT, INSERT, UPDATE      CredencialesServiceImpl, AuthServiceImpl,
--                                              ClienteServiceImpl.actualizarCliente
--     cuenta       SELECT (cliente_id, numero_cuenta, tipo_cuenta, estado)
--                                              CuentasDelClienteJdbcRepository (HU-02)
--     auditoria_datos_cliente  INSERT          ClienteServiceImpl.actualizarCliente (HU-03)
--   digitalbank_core      core-api: cuentas, depósitos, retiros, transferencias, estados,
--                         inactivación de clientes (HU-04) y reversos (HU-12)
--     cliente      SELECT (id, tipo_documento, numero_documento, estado)
--                                              ClienteConsultaAdapter (apertura HU-05),
--                                              ClienteEstadoService (HU-04)
--                  UPDATE (estado)             ClienteEstadoService (HU-04)
--     cuenta       SELECT, INSERT, UPDATE      CuentaServiceImpl, EstadoCuentaServiceImpl,
--                                              InactivacionClienteServiceImpl
--     transaccion  SELECT, INSERT              Deposito/Retiro/TransferenciaServiceImpl,
--                                              ReversoServiceImpl, TransaccionJpaRepository
--                  UPDATE (estado, transaccion_relacionada_id)
--                                              ReversoServiceImpl (HU-12)
--     movimiento_administrativo_cuenta   INSERT  EstadoCuentaServiceImpl, InactivacionClienteServiceImpl
--     movimiento_administrativo_cliente  INSERT  ClienteEstadoService (HU-04)
--   digitalbank_reportes  reportes-api: historial (HU-13) y detalle de una transacción (HU-14)
--     cuenta       SELECT (id, cliente_id, numero_cuenta)   MovimientoJdbcRepository,
--                                                           TransaccionJdbcRepository
--     transaccion  SELECT                                   MovimientoJdbcRepository,
--                                                           TransaccionJdbcRepository
--   Ningún rol tiene DELETE, ni UPDATE sobre las tablas de auditoría:
--   desde las APIs esos registros solo se pueden agregar. En transaccion
--   la única escritura después del INSERT es la de la HU-12, que solo
--   puede tocar estado y transaccion_relacionada_id, nunca el monto ni
--   los saldos.
--   cliente y transaccion tienen UPDATE por columna porque sus entidades,
--   Cliente en auth-api y Transaccion en core-api, usan @DynamicUpdate:
--   Hibernate solo pone en el UPDATE las columnas que cambiaron. El
--   UPDATE de core sobre cliente es SQL directo en ClienteEstadoService
--   y solo toca estado. usuario y cuenta siguen con UPDATE por tabla
--   porque las entidades Usuario y Cuenta no tienen @DynamicUpdate, así
--   que Hibernate escribe todas sus columnas en cada save, aunque solo
--   haya cambiado una.
--
-- Cómo aplicarlo:
--   Con el usuario dueño de las tablas (postgres en Supabase), desde el
--   SQL Editor o con psql. Antes, reemplazar las tres claves de la
--   sección 1; si se deja alguna sin cambiar, el script se detiene sin
--   hacer nada. Se puede correr más de una vez: no duplica roles ni
--   políticas, y en una segunda corrida solo cambia las claves.
--   Correrlo no cambia nada para las APIs mientras sigan conectándose
--   con postgres; el cambio llega cuando en Render se cambia el usuario
--   de cada servicio (ver el documento de roles y seguridad).
--   Ojo con core-api: corre Flyway al arrancar con spring.flyway.enabled=true
--   y no define spring.flyway.user, así que Flyway usa el mismo usuario
--   de la conexión, DB_USERNAME. Si en Render se le pone digitalbank_core,
--   Flyway no podrá leer flyway_schema_history ni aplicar migraciones,
--   porque ese rol no es dueño de las tablas ni puede crear objetos en
--   public, y core-api no arrancará. Antes de cambiar ese usuario hay
--   que darle a Flyway su propio usuario dueño de las tablas, por ejemplo
--   con spring.flyway.user y spring.flyway.password. auth-api tiene
--   Flyway apagado y reportes-api no lo usa, así que a ellas no les pasa.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. Roles de conexión, uno por API
-- ---------------------------------------------------------------------
DO $$
DECLARE
    clave_auth     text := '<CLAVE_AUTH_API>';
    clave_core     text := '<CLAVE_CORE_API>';
    clave_reportes text := '<CLAVE_REPORTES_API>';
    rol   text;
    clave text;
BEGIN
    IF clave_auth LIKE '<%' OR clave_core LIKE '<%' OR clave_reportes LIKE '<%' THEN
        RAISE EXCEPTION 'Reemplace las claves <CLAVE_...> antes de correr el script';
    END IF;

    FOR rol, clave IN
        SELECT * FROM (VALUES ('digitalbank_auth',     clave_auth),
                              ('digitalbank_core',     clave_core),
                              ('digitalbank_reportes', clave_reportes)) AS r(rol, clave)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = rol) THEN
            EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L '
                           'NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS', rol, clave);
        ELSE
            EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L', rol, clave);
        END IF;
    END LOOP;
END $$;

-- reportes-api solo lee: toda transacción de su sesión es de solo lectura,
-- aunque el código intentara escribir. El límite de tiempo evita que una
-- consulta de historial muy grande quede corriendo.
ALTER ROLE digitalbank_reportes SET default_transaction_read_only = on;
ALTER ROLE digitalbank_reportes SET statement_timeout = '15s';

-- ---------------------------------------------------------------------
-- 2. Esquema
-- ---------------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO digitalbank_auth, digitalbank_core, digitalbank_reportes;

-- Nadie fuera del dueño crea objetos en public (en PostgreSQL 15+ ya es así;
-- se deja explícito).
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- ---------------------------------------------------------------------
-- 3. Permisos sobre tablas
-- Primero se quita todo, para que volver a correr el script deje
-- exactamente lo que está aquí.
-- ---------------------------------------------------------------------
-- En PostgreSQL, REVOKE sobre una tabla también quita los permisos por
-- columna de esa tabla, así que este REVOKE deja limpios los SELECT y
-- UPDATE por columna que hubiera dejado una corrida anterior.
REVOKE ALL ON cliente, usuario, cuenta, transaccion, movimiento_administrativo_cuenta,
              auditoria_datos_cliente, movimiento_administrativo_cliente
    FROM digitalbank_auth, digitalbank_core, digitalbank_reportes;

-- auth-api
GRANT SELECT, INSERT         ON cliente TO digitalbank_auth;
-- HU-03: ClienteServiceImpl carga el cliente y lo guarda; con @DynamicUpdate
-- el UPDATE solo lleva email y telefono.
GRANT UPDATE (email, telefono) ON cliente TO digitalbank_auth;
GRANT SELECT, INSERT, UPDATE ON usuario TO digitalbank_auth;
-- HU-02: CuentasDelClienteJdbcRepository lee número, tipo y estado de las
-- cuentas y filtra por cliente_id. Sin saldos.
GRANT SELECT (cliente_id, numero_cuenta, tipo_cuenta, estado) ON cuenta TO digitalbank_auth;
-- HU-03: una fila por cada dato que cambia.
GRANT INSERT                 ON auditoria_datos_cliente TO digitalbank_auth;

-- core-api
GRANT SELECT (id, tipo_documento, numero_documento, estado) ON cliente TO digitalbank_core;
-- HU-04: ClienteEstadoService hace SELECT estado FROM cliente ... FOR UPDATE
-- y luego UPDATE cliente SET estado. En PostgreSQL el FOR UPDATE exige
-- permiso de UPDATE sobre alguna columna de la tabla y, con RLS, una
-- política de UPDATE. Sin la política no sale error: la fila no se
-- bloquea, el SELECT no devuelve nada y el UPDATE afecta cero filas.
GRANT UPDATE (estado) ON cliente TO digitalbank_core;
-- El trigger fn_validar_cliente_activo_cuenta de V7, que valida el
-- cliente al abrir una cuenta, corre con el rol de core y consulta
-- cliente.id y cliente.estado. core ya tiene SELECT sobre esas dos
-- columnas, así que no hace falta nada más.
GRANT SELECT, INSERT, UPDATE ON cuenta                           TO digitalbank_core;
-- HU-12: ReversoServiceImpl carga la transacción original con FOR UPDATE,
-- inserta el REVERSO y marca la original REVERSADA apuntando al reverso.
-- Con @DynamicUpdate ese UPDATE solo lleva estado y transaccion_relacionada_id.
-- El trigger fn_proteger_transaccion de V7 también corre con el rol de core
-- y consulta transaccion para comprobar el enlace con el reverso, así que
-- además del FOR UPDATE de la HU-12 el SELECT lo necesita el trigger.
GRANT SELECT, INSERT         ON transaccion                      TO digitalbank_core;
GRANT UPDATE (estado, transaccion_relacionada_id) ON transaccion TO digitalbank_core;
GRANT INSERT                 ON movimiento_administrativo_cuenta TO digitalbank_core;
-- HU-04: auditoría de la inactivación.
GRANT INSERT                 ON movimiento_administrativo_cliente TO digitalbank_core;

-- reportes-api
-- HU-13 y HU-14 leen de cuenta solo id, cliente_id y numero_cuenta.
GRANT SELECT (id, cliente_id, numero_cuenta) ON cuenta TO digitalbank_reportes;
GRANT SELECT                 ON transaccion            TO digitalbank_reportes;

-- ---------------------------------------------------------------------
-- 4. Roles de la API REST de Supabase (anon, authenticated)
-- El backend no usa la API REST de Supabase, así que esos roles no
-- necesitan nada. Si no existen (base local), este bloque no hace nada.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    rol text;
BEGIN
    FOREACH rol IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = rol) THEN
            EXECUTE format('REVOKE ALL ON cliente, usuario, cuenta, transaccion, '
                           'movimiento_administrativo_cuenta, auditoria_datos_cliente, '
                           'movimiento_administrativo_cliente FROM %I', rol);
            -- Que las tablas de migraciones futuras tampoco les queden abiertas.
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public '
                           'REVOKE ALL ON TABLES FROM %I', rol);
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- 5. Row Level Security
-- Con RLS activo, un rol sin política no ve ni modifica ninguna fila,
-- aunque tenga permisos sobre la tabla. Las políticas de abajo dejan
-- pasar todas las filas a cada API en las tablas que usa; lo que puede
-- hacer en cada una lo siguen limitando los GRANT de la sección 3.
-- Qué cuentas ve cada cliente lo decide la aplicación (ADR-0005), no la
-- base, porque cada API usa un solo rol para todos sus usuarios.
-- ---------------------------------------------------------------------
ALTER TABLE cliente                           ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario                           ENABLE ROW LEVEL SECURITY;
ALTER TABLE cuenta                            ENABLE ROW LEVEL SECURITY;
ALTER TABLE transaccion                       ENABLE ROW LEVEL SECURITY;
ALTER TABLE movimiento_administrativo_cuenta  ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria_datos_cliente           ENABLE ROW LEVEL SECURITY;
ALTER TABLE movimiento_administrativo_cliente ENABLE ROW LEVEL SECURITY;

-- auth-api
-- pol_auth_cliente es FOR ALL, así que ya cubre el UPDATE de correo y
-- teléfono de la HU-03; qué columnas puede cambiar lo limita el GRANT.
DROP POLICY IF EXISTS pol_auth_cliente ON cliente;
CREATE POLICY pol_auth_cliente ON cliente FOR ALL TO digitalbank_auth USING (true) WITH CHECK (true);
DROP POLICY IF EXISTS pol_auth_usuario ON usuario;
CREATE POLICY pol_auth_usuario ON usuario FOR ALL TO digitalbank_auth USING (true) WITH CHECK (true);
DROP POLICY IF EXISTS pol_auth_cuenta ON cuenta;
CREATE POLICY pol_auth_cuenta ON cuenta FOR SELECT TO digitalbank_auth USING (true);
DROP POLICY IF EXISTS pol_auth_auditoria_datos_cliente ON auditoria_datos_cliente;
CREATE POLICY pol_auth_auditoria_datos_cliente ON auditoria_datos_cliente
    FOR INSERT TO digitalbank_auth WITH CHECK (true);

-- core-api
DROP POLICY IF EXISTS pol_core_cliente ON cliente;
CREATE POLICY pol_core_cliente ON cliente FOR SELECT TO digitalbank_core USING (true);
-- HU-04: sin esta política el SELECT ... FOR UPDATE y el UPDATE de estado
-- no encuentran la fila. Va aparte de pol_core_cliente para que core no
-- quede con una política de INSERT o DELETE en cliente.
DROP POLICY IF EXISTS pol_core_cliente_update ON cliente;
CREATE POLICY pol_core_cliente_update ON cliente FOR UPDATE TO digitalbank_core USING (true) WITH CHECK (true);
DROP POLICY IF EXISTS pol_core_cuenta ON cuenta;
CREATE POLICY pol_core_cuenta ON cuenta FOR ALL TO digitalbank_core USING (true) WITH CHECK (true);
-- transaccion: core necesita SELECT, INSERT y UPDATE, así que la política
-- es FOR ALL, igual que en cuenta. El DELETE lo sigue negando el GRANT.
DROP POLICY IF EXISTS pol_core_transaccion ON transaccion;
CREATE POLICY pol_core_transaccion ON transaccion FOR ALL TO digitalbank_core USING (true) WITH CHECK (true);
DROP POLICY IF EXISTS pol_core_mov_admin ON movimiento_administrativo_cuenta;
CREATE POLICY pol_core_mov_admin ON movimiento_administrativo_cuenta FOR INSERT TO digitalbank_core WITH CHECK (true);
DROP POLICY IF EXISTS pol_core_mov_admin_cliente ON movimiento_administrativo_cliente;
CREATE POLICY pol_core_mov_admin_cliente ON movimiento_administrativo_cliente
    FOR INSERT TO digitalbank_core WITH CHECK (true);

-- reportes-api
DROP POLICY IF EXISTS pol_reportes_cuenta ON cuenta;
CREATE POLICY pol_reportes_cuenta ON cuenta FOR SELECT TO digitalbank_reportes USING (true);
DROP POLICY IF EXISTS pol_reportes_transaccion ON transaccion;
CREATE POLICY pol_reportes_transaccion ON transaccion FOR SELECT TO digitalbank_reportes USING (true);

COMMIT;


-- =====================================================================
-- 6. Verificación
-- =====================================================================

-- Matriz de permisos: qué puede hacer cada rol en cada tabla.
-- En cuenta, cliente y transaccion, SELECT o UPDATE sale false para los
-- roles que solo tienen algunas columnas; la segunda consulta muestra
-- esas columnas.
SELECT r.rol, t.tabla,
       has_table_privilege(r.rol, t.tabla, 'SELECT') AS sel,
       has_table_privilege(r.rol, t.tabla, 'INSERT') AS ins,
       has_table_privilege(r.rol, t.tabla, 'UPDATE') AS upd,
       has_table_privilege(r.rol, t.tabla, 'DELETE') AS del
FROM (SELECT rolname AS rol FROM pg_roles
      WHERE rolname IN ('digitalbank_auth', 'digitalbank_core', 'digitalbank_reportes',
                        'anon', 'authenticated')) r
CROSS JOIN (VALUES ('cliente'), ('usuario'), ('cuenta'), ('transaccion'),
                   ('movimiento_administrativo_cuenta'), ('auditoria_datos_cliente'),
                   ('movimiento_administrativo_cliente')) t(tabla)
ORDER BY r.rol, t.tabla;

-- Permisos por columna: SELECT y UPDATE que un rol tiene solo sobre
-- algunas columnas de la tabla.
SELECT grantee AS rol, table_name AS tabla, privilege_type AS permiso,
       string_agg(column_name, ', ' ORDER BY column_name) AS columnas
FROM information_schema.column_privileges
WHERE grantee IN ('digitalbank_auth', 'digitalbank_core', 'digitalbank_reportes')
  AND privilege_type IN ('SELECT', 'UPDATE')
  AND table_name IN ('cliente', 'cuenta', 'transaccion')
  AND NOT has_table_privilege(grantee, table_name, privilege_type)
GROUP BY grantee, table_name, privilege_type
ORDER BY 1, 2, 3;

-- RLS activo y políticas por tabla, con la operación que cubre cada una.
SELECT c.relname AS tabla, c.relrowsecurity AS rls_activo,
       string_agg(p.polname || ' ' ||
           CASE p.polcmd WHEN 'r' THEN 'SELECT' WHEN 'a' THEN 'INSERT'
                         WHEN 'w' THEN 'UPDATE' WHEN 'd' THEN 'DELETE' ELSE 'ALL' END ||
           ' (' || array_to_string(ARRAY(
           SELECT rolname FROM pg_roles WHERE oid = ANY (p.polroles)), ',') || ')', '; '
           ORDER BY p.polname) AS politicas
FROM pg_class c
LEFT JOIN pg_policy p ON p.polrelid = c.oid
WHERE c.relname IN ('cliente', 'usuario', 'cuenta', 'transaccion', 'movimiento_administrativo_cuenta',
                    'auditoria_datos_cliente', 'movimiento_administrativo_cliente')
  AND c.relnamespace = 'public'::regnamespace
GROUP BY c.relname, c.relrowsecurity
ORDER BY c.relname;

-- Configuración de los roles.
SELECT rolname, rolcanlogin, rolsuper, rolbypassrls, rolconfig
FROM pg_roles
WHERE rolname LIKE 'digitalbank\_%'
ORDER BY rolname;
