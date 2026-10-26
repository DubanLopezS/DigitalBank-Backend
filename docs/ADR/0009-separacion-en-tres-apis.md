# ADR-0009: Separar el backend en tres APIs con una base de datos compartida

**Fecha:** 8 de octubre de 2026
**Responsable:** Equipo Digital Bank Backend
**Estado:** Aceptado

**Nota de actualización (25 de octubre de 2026):** lo actualizamos después de la HU-13. `reportes-api` ya tiene seguridad, conexión a la base y el endpoint de historial, y valida tokens igual que `core-api`. También volvimos a contar las clases repetidas, ahora con `reportes-api`, y corregimos lo que decía de `SecurityConfig`, que no es igual en las tres APIs.

## Contexto

Hasta el Sprint 1 el backend fue un monolito modular: un solo proceso con los módulos `auth`, `cliente`, `cuenta` y `transaccion`. Para el Sprint 2 el requerimiento es separarlo en Web APIs independientes (procesos distintos), sin llegar a microservicios estrictos. Con eso aparecieron varias preguntas: dónde vive cada módulo, quién emite los tokens, quién corre las migraciones y cómo compartimos el código común.

## Alternativas que consideramos

- **Tres repositorios, uno por API**: se parece más a un entorno real, pero toca repetir toda la configuración tres veces y no hay forma limpia de compartir código Java sin publicar una librería.
- **Una base de datos por API**: es lo correcto en teoría, pero queda fuera del alcance de tres sprints.
- **Dejar `cliente` en `core-api`**: el registro de cliente crea el Cliente y su Usuario en una sola transacción. Si `cliente` y `auth` quedan en procesos distintos, esa transacción se rompe y habría que compensar a mano.
- **Validar el token llamando a `auth-api` en cada request**: permite revocar un token al instante, pero cada request depende de que `auth-api` esté despierta, suma latencia y, si `auth-api` se cae, se cae todo el sistema. Lo descartamos.
- **Que `core-api` consulte clientes por HTTP a `auth-api`**: respeta que cada API sea dueña de sus datos, pero agrega un endpoint, un cliente HTTP y una dependencia de que `auth-api` esté despierta. Lo descartamos por simplicidad.

## Qué decidimos

Un solo repositorio (monorepo) con un `pom.xml` padre y cuatro módulos:

- `auth-api`: módulos `auth` y `cliente`. Es la única que crea tokens. Como `cliente` vive acá, el registro sigue siendo una sola transacción. El nombre no describe todo lo que contiene, pero lo aceptamos.
- `core-api`: módulos `cuenta` y `transaccion`. También tiene un paquete `cliente` pequeño, con `ClienteConsultaAdapter` para leer clientes de la base.
- `reportes-api`: solo consultas, de solo lectura. Tiene el módulo `movimiento` con el historial de movimientos de una cuenta (`GET /api/reportes/cuentas/{cuentaId}/movimientos`, HU-13). Lee la base con `JdbcClient`, sin JPA ni entidades (ADR-0011).
- `common`: lo que comparten las tres (por ahora, manejo de excepciones).

Las tres APIs usan la misma base de Supabase. La única que corre Flyway es `core-api`. En el primer despliegue, y cada vez que entra una migración nueva, `core-api` tiene que arrancar primero. `auth-api` tiene `ddl-auto=validate`, así que no arranca si le faltan tablas o columnas. `reportes-api` no tiene JPA ni `ddl-auto`: arranca igual, pero sus consultas responderían 500 hasta que el esquema exista; ningún test cubre ese caso.

Solo `auth-api` crea tokens (es la única que tiene `generarToken` y la entidad `Usuario`). `core-api` y `reportes-api` validan el token localmente, con el mismo secreto (`JWT_SECRET`) y una versión de solo lectura de `IJwtService`. El token ya trae el id de usuario y el rol, y el `clienteId` solo cuando el usuario es un cliente, así que no hace falta consultar a `auth-api`. La forma de firmar el token y lo que implica quedó en el ADR-0010.

No hay llamadas HTTP entre APIs. Para consultar un cliente por documento al abrir una cuenta, `core-api` implementa `IClienteConsulta` con una consulta de solo lectura a la tabla `cliente` de la base compartida. `CuentaServiceImpl` sigue dependiendo de una interfaz, no de la implementación concreta, así que el cambio de módulo a `core-api` no obligó a tocar su lógica de negocio, solo cambió la interfaz que usa: antes era `IClienteService` y ahora es `IClienteConsulta`.

En Render hay un Web Service por API, cada uno con su propio Dockerfile y la raíz del repo como contexto de build. Las tres APIs leen las mismas variables de conexión y el mismo `JWT_SECRET`; que en Render tengan los mismos valores es dato del equipo, no verificable en el repo. Que `reportes-api` ya esté desplegado y que los tres servicios tengan el pool de conexiones limitado también es dato del equipo (ADR-0012). `core-api`, al ser la única con Flyway activo, es la que corre las migraciones; `auth-api` lo tiene apagado con `spring.flyway.enabled=false` y `reportes-api` no tiene la dependencia de Flyway.

## Qué implica esto

**A favor:** el registro de cliente conserva su transacción única, cada API se despliega por separado, ninguna API depende de que otra esté disponible para atender una petición y, como ya trabajamos con interfaces, cambiar la forma de obtener datos de otro módulo tocó poco los servicios: en `CuentaServiceImpl` solo cambió la interfaz que usa, y `SaldoServiceImpl` dejó de depender de `IUsuarioQueryService` y ahora recibe el `clienteId`, que viene en el token.

**En contra:**

- Con la base compartida, un cambio de esquema puede afectar a las tres APIs. En `reportes-api` el error no aparecería al arrancar sino en la primera consulta, porque no tiene `ddl-auto=validate`.
- No se puede revocar un token antes de que venza. Se mitiga en parte porque las operaciones consultan el estado real de la cuenta en la base de datos y el token dura 60 minutos (`security.jwt.expiration-minutes` en `auth-api`, ADR-0010).
- `core-api` queda acoplada a las columnas de la tabla `cliente` (solo lectura), y `reportes-api` a las de `cuenta` y `transaccion`.
- `common` debe mantenerse pequeño para no volver a acoplar las APIs.
- Las APIs comparten el mismo `JWT_SECRET`, así que cualquier API que lo tenga podría firmar tokens, no solo `auth-api`. La firma asimétrica como alternativa quedó en el ADR-0010.
- Hay siete clases con el mismo paquete y nombre repetidas entre las APIs:
  - `OpenApiConfig` está en las tres y es igual en las tres.
  - `TipoDocumento` está en `auth-api` y `core-api` y es igual en las dos.
  - `SecurityConfig` está en las tres y no es igual. El 403 de `auth-api` dice "No tienes permisos para realizar esta acción" y el de las otras dos "Acceso no autorizado". `core-api` todavía deja pasar `/api/auth/login`, que no existe en esa API. Solo `reportes-api` le pone `UTF-8` a las respuestas de 401 y 403.
  - `IJwtService`, `JwtServiceImpl` y `JwtAuthenticationFilter` están en las tres y cambian según lo que hace cada API. Las de `auth-api` firman tokens y su filtro no guarda el `clienteId`. Las de `core-api` y `reportes-api` solo validan, y sus filtros hacen lo mismo.
  - `ClienteResumenResponse` está en `auth-api` y `core-api`. La de `auth-api` tiene además el método `from(Cliente)`.

  Si se cambia una de estas clases hay que revisar las otras copias a mano. Lo aceptamos para dejar `common` solo con excepciones.
- Quedó código sin consumidor en producción después de la separación: `IUsuarioQueryService` y `UsuarioQueryServiceImpl` en `auth-api` (solo los usa su test), y en `auth-api` también `obtenerResumenPorDocumento` de `IClienteService` con su `ClienteResumenResponse`, que eran para `CuentaServiceImpl` antes de que pasara a `IClienteConsulta`.
- Que `core-api` solo lea `cliente` depende del código (`ClienteConsultaAdapter` solo hace `SELECT`), no de los permisos de la base. Con `reportes-api` pasa lo mismo (`MovimientoJdbcRepository` solo hace `SELECT`). Separar usuarios de base por API queda como mejora.
