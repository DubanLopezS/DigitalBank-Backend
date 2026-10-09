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

## Qué decidimos

Un solo repositorio (monorepo) con un `pom.xml` padre y cuatro módulos:

- `auth-api`: módulos `auth` y `cliente`. Es la única que crea tokens. Como `cliente` vive acá, el registro sigue siendo una sola transacción. El nombre no describe todo lo que contiene, pero lo aceptamos.
- `core-api`: módulos `cuenta` y `transaccion`, con las HU del Sprint 2.
- `reportes-api`: solo consultas, de solo lectura.
- `common`: lo que comparten las tres (manejo de excepciones y utilidades).

Las tres APIs usan la misma base de Supabase. La única que corre Flyway es `core-api`. `auth-api` y `reportes-api` llevan `spring.flyway.enabled=false`. En el primer despliegue `core-api` arranca primero, porque con `ddl-auto=validate` las otras fallarían si faltan las tablas.

Solo `auth-api` crea tokens (es la única que tiene `generarToken` y la entidad `Usuario`). `core-api` y `reportes-api` validan el token localmente, con el mismo secreto (`JWT_SECRET`) y una versión de solo lectura de `IJwtService`. El token ya trae el id de usuario, el rol y el `clienteId`, así que no hace falta consultar a `auth-api`. 

En Render habrá tres Web Services: el contexto de build es la raíz del repo y cada servicio apunta a su Dockerfile (`auth-api/Dockerfile`, etc.). Cada servicio replica las variables de Supabase y los reintentos de Flyway.

## Qué implica esto

**A favor:** el registro de cliente conserva su transacción única, cada API se despliega por separado, la validación del token no depende de que `auth-api` esté disponible y, como ya trabajamos con interfaces, cambiar a adaptadores HTTP no obliga a tocar los servicios.

**En contra:**

- Con la base compartida, un cambio de esquema puede afectar a las tres APIs.
- No se puede revocar un token antes de que venza. Se mitiga porque las operaciones consultan el estado real de la cuenta en la base de datos y el token dura poco.
- El arranque en frío de Render solo afecta a `core-api` cuando consulta un cliente en `auth-api`, no en cada request.
- `reportes-api` lee las tablas directamente, así que queda acoplada al esquema.
- `common` debe mantenerse pequeño para no volver a acoplar las APIs.
