# ADR-0009: Separar el backend en tres APIs con una base de datos compartida

**Fecha:** 8 de octubre de 2026
**Responsable:** Equipo Digital Bank Backend

## Contexto

Hasta el Sprint 1 el backend fue un monolito modular: un solo proceso con los módulos `auth`, `cliente`, `cuenta` y `transaccion`. Para el Sprint 2 el requerimiento es separarlo en Web APIs independientes (procesos distintos), sin llegar a microservicios estrictos. Con eso aparecieron varias preguntas: dónde vive cada módulo, quién emite los tokens, quién corre las migraciones y cómo compartimos el código común.

## Alternativas que consideramos

- **Tres repositorios, uno por API**: se parece más a un entorno real, pero toca repetir toda la configuración tres veces y no hay forma limpia de compartir código Java sin publicar una librería.
- **Una base de datos por API**: es lo correcto en teoría, pero queda fuera del alcance de tres sprints.
- **Dejar `cliente` en `core-api`**: el registro de cliente crea el Cliente y su Usuario en una sola transacción. Si `cliente` y `auth` quedan en procesos distintos, esa transacción se rompe y habría que compensar a mano.
- **Validar el token llamando a `auth-api` en cada request**: permite revocar un token al instante, pero cada request depende de que `auth-api` esté despierto, suma latencia y, si `auth-api` se cae, se cae todo el sistema. Lo descartamos.
- **Que `core-api` consulte clientes por HTTP a `auth-api`**: respeta que cada API sea dueña de sus datos, pero agrega un endpoint, un cliente HTTP y una dependencia de que `auth-api` esté despierto. Lo descartamos por simplicidad.

## Qué decidimos

Un solo repositorio (monorepo) con un `pom.xml` padre y cuatro módulos:

- `auth-api`: módulos `auth` y `cliente`. Es la única que crea tokens. Como `cliente` vive acá, el registro sigue siendo una sola transacción. El nombre no describe todo lo que contiene, pero lo aceptamos.
- `core-api`: módulos `cuenta` y `transaccion`.
- `reportes-api`: solo consultas, de solo lectura.
- `common`: lo que comparten las tres (Por ahora, manejo de excepciones).

Las tres APIs usarán la misma base de Supabase. La única que corre Flyway es `core-api`. En el primer despliegue `core-api` arranca primero, porque con `ddl-auto=validate` las otras fallarían si faltan las tablas.

Solo `auth-api` crea tokens (es la única que tiene `generarToken` y la entidad `Usuario`). `core-api` valida el token localmente, como en un futuro tambien lo hará `reportes-api`, con el mismo secreto (`JWT_SECRET`) y una versión de solo lectura de `IJwtService`. El token ya trae el id de usuario, el rol y el `clienteId`, así que no hace falta consultar a `auth-api`.

No hay llamadas HTTP entre APIs. Para consultar un cliente por documento al abrir una cuenta, `core-api` implementa `IClienteConsulta` con una consulta de solo lectura a la tabla `cliente` de la base compartida. `CuentaServiceImpl` sigue dependiendo de una interfaz, no de la implementación concreta, así que el cambio de módulo a `core-api` no obligó a tocar su lógica de negocio, solo cambió qué clase implementa esa interfaz.

En Render habrá un Web Service por API desplegada: el contexto de build es la raíz del repo y cada uno va a apuntar a su propio Dockerfile. Por ahora solo se tiene pensado desplegar auth-api y core-api, reportes-api queda sin desplegar hasta tener lógica propia. Cada servicio repite las variables de conexión a Supabase y comparte el mismo JWT_SECRET. core-api, al ser la única con Flyway activo.

## Qué implica esto

**A favor:** el registro de cliente conserva su transacción única, cada API se despliega por separado, ninguna API depende de que otra esté disponible para atender una petición y, como ya trabajamos con interfaces, cambiar la forma de obtener datos de otro módulo no obliga a tocar los servicios.

**En contra:**

- Con la base compartida, un cambio de esquema puede afectar a las tres APIs.
- No se puede revocar un token antes de que venza. Se mitiga porque las operaciones consultan el estado real de la cuenta en la base de datos y el token dura poco.
- `core-api` queda acoplada a las columnas de la tabla `cliente` (solo lectura), igual que `reportes-api` lo estará con las tablas que consulte.
- `common` debe mantenerse pequeño para no volver a acoplar las APIs.

