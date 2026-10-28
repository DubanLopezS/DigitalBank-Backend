# Digital Bank Backend

Backend de un banco digital. Es un monorepo Maven con Spring Boot y Java 21, dividido en tres APIs y un módulo común. Las tres APIs usan la misma base de datos PostgreSQL.

- `auth-api`: login y registro de clientes.
- `core-api`: cuentas, saldo, depósitos, retiros, transferencias, bloqueo y cierre de cuentas. Es la que corre las migraciones de Flyway.
- `reportes-api`: historial de movimientos de una cuenta. Solo consulta.
- `common`: clases compartidas por las tres APIs, como el manejo de errores (`GlobalExceptionHandler`).

## Autenticación

El token se pide a `auth-api` con `POST /api/auth/login`:

```json
{
  "email": "correo@example.com",
  "password": "clave-de-ejemplo"
}
```

Si las credenciales son correctas responde `200`:

```json
{
  "token": "<jwt>",
  "tokenType": "Bearer",
  "expiresInMinutes": 60
}
```

Los demás endpoints piden el token en el header:

```text
Authorization: Bearer <token>
```

El token dura 60 minutos (`security.jwt.expiration-minutes` en el `application.properties` de `auth-api`). Los roles son `CLIENTE`, `ADMIN` y `CAJERO`.

Si un usuario falla la contraseña 3 veces, la cuenta se bloquea 1 hora. Los tres intentos fallidos responden `401` y desde el cuarto responde `429`, aunque la contraseña sea correcta. Pasada la hora se desbloquea en el siguiente login.

El token lo crea solo `auth-api`. `core-api` y `reportes-api` lo validan con el mismo secreto (`JWT_SECRET`), sin llamar a `auth-api`. El detalle está en [docs/ADR/0010-jwt-hmac-secreto-compartido.md](docs/ADR/0010-jwt-hmac-secreto-compartido.md).

## Primer ADMIN y CAJERO

No hay endpoint para crear usuarios ADMIN o CAJERO. Para eso está [docs/scripts/crear-usuarios-internos.sql](docs/scripts/crear-usuarios-internos.sql). Se corre una vez contra la base, después de que core-api haya aplicado las migraciones, por ejemplo desde el SQL Editor de Supabase.

Antes de correrlo hay que cambiar los correos y contraseñas que están entre `< >` en el script.

## Variables de entorno

| Variable | API | Para qué sirve |
|---|---|---|
| `DB_HOST` | las tres | Host de la base PostgreSQL |
| `DB_PORT` | las tres | Puerto de la base. Opcional, tiene valor por defecto |
| `DB_NAME` | las tres | Nombre de la base. Opcional, tiene valor por defecto |
| `DB_USERNAME` | las tres | Usuario de la base |
| `DB_PASSWORD` | las tres | Contraseña de la base |
| `PORT` | las tres | Puerto HTTP de la API. Opcional, tiene valor por defecto |
| `JWT_SECRET` | las tres | Secreto con el que se firma y se valida el token |

`JWT_SECRET` tiene que ser igual en las tres APIs. Si no, una API rechaza los tokens que crea `auth-api`. Debe tener al menos 32 bytes, porque `Keys.hmacShaKeyFor` no acepta claves más cortas y la API no arrancaría.

La conexión a la base usa `sslmode=require`. El límite del pool de conexiones en Render está explicado en [docs/ADR/0012-limite-de-conexiones-y-render-free.md](docs/ADR/0012-limite-de-conexiones-y-render-free.md).

## Cómo construir y correr

Cada API tiene su `Dockerfile` y se construye desde la raíz del repo. Este es el comando que corre cada Dockerfile:

| API | Comando de construcción | Puerto que expone |
|---|---|---|
| `auth-api` | `./mvnw -pl auth-api -am clean package -B` | 8081 |
| `core-api` | `./mvnw -pl core-api -am clean package -B` | 8082 |
| `reportes-api` | `./mvnw -pl reportes-api -am clean package -B` | 8083 |

El `-am` también construye `common`. Los tests corren al construir. La imagen arranca con `java -Xmx400m -jar app.jar`.

Para arrancar, cada API necesita como mínimo `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET`. Conviene levantar `core-api` primero, porque crea las tablas; `auth-api` valida el esquema al arrancar (`ddl-auto=validate`).

## Endpoints

La prioridad y la razón de cada endpoint están en [docs/apis-priorizadas.md](docs/apis-priorizadas.md).

### Sprint 1

| Método y ruta | HU | Qué hace | Rol | Códigos de respuesta |
|---|---|---|---|---|
| `POST /api/auth/login` | HU-18 Login usuario | Autentica un usuario y devuelve un JWT | Público | `200` login exitoso · `400` email/password vacíos o email con formato inválido · `401` credenciales inválidas · `429` cuenta bloqueada 1 hora tras 3 intentos fallidos |
| `POST /api/clientes` | HU-01 Registro de cliente | Registra un cliente nuevo y crea su usuario en la misma transacción | ADMIN | `201` creado · `400` datos inválidos o menor de edad · `401` sin token o token inválido · `403` no es ADMIN · `409` documento o email ya existe |
| `POST /api/cuentas` | HU-05 Apertura de cuenta | El cliente abre una cuenta de ahorros o corriente a su propio nombre | CLIENTE (titular; validado en el servicio) | `201` creada · `400` datos inválidos · `401` sin token o token inválido · `403` el documento no es del cliente autenticado, o el cliente no está activo · `404` documento no encontrado |
| `POST /api/cuentas/{cuentaId}/depositos` | HU-10 Depósito a cuenta | Registra un depósito y actualiza el saldo | ADMIN, CAJERO | `201` creado · `400` monto inválido u origen faltante · `401` sin token o token inválido · `403` no es ADMIN/CAJERO · `404` cuenta no encontrada · `409` cuenta no disponible (cerrada) |
| `GET /api/cuentas/{numeroCuenta}/saldo` | HU-06 Consulta de saldo | Devuelve el saldo contable y disponible de una cuenta | CLIENTE (solo la suya), ADMIN, CAJERO (cualquiera) | `200` ok · `401` sin token o token inválido · `403` cuenta ajena (CLIENTE) · `404` cuenta no encontrada (solo si consulta ADMIN/CAJERO) |

### Sprint 2

| Método y ruta | HU | Qué hace | Rol | Códigos de respuesta |
|---|---|---|---|---|
| `POST /api/cuentas/{cuentaId}/bloquear` | HU-07a Bloqueo de cuenta | Bloquea una cuenta activa, con auditoría del motivo | ADMIN | `200` ok · `400` motivo inválido (menos de 10 o más de 255 caracteres) · `401` sin token o token inválido · `403` no es ADMIN · `404` cuenta no encontrada · `409` la cuenta no está activa |
| `POST /api/cuentas/{cuentaId}/desbloquear` | HU-07a Bloqueo de cuenta | Devuelve a ACTIVA una cuenta bloqueada | ADMIN | `200` ok · `400` motivo inválido · `401` sin token o token inválido · `403` no es ADMIN · `404` cuenta no encontrada · `409` la cuenta no está bloqueada |
| `POST /api/cuentas/{cuentaId}/cerrar` | HU-07b Cierre de cuenta | Cierra una cuenta de forma permanente (exige saldo $0) | ADMIN | `200` ok · `400` motivo inválido · `401` sin token o token inválido · `403` no es ADMIN · `404` cuenta no encontrada · `409` ya está cerrada, o no tiene saldo $0 |
| `POST /api/cuentas/{cuentaId}/retiros` | HU-11 Retiro de cuenta | El cliente titular retira dinero de su cuenta | CLIENTE (titular; validado en el servicio) | `201` creado · `400` monto inválido · `401` sin token o token inválido · `403` la cuenta no existe o no es del cliente autenticado · `409` cuenta no disponible, o saldo insuficiente |
| `POST /api/cuentas/{cuentaId}/transferencias` | HU-09a Transferencia exitosa · HU-09b Validación de montos y fondos · HU-09c Validación de cuenta destino | Transfiere dinero entre dos cuentas, generando dos movimientos ligados | CLIENTE (titular de la cuenta origen; validado en el servicio) | `201` creado · `400` monto inválido, o cuenta origen igual a la destino · `401` sin token o token inválido · `403` cuenta origen no es del cliente autenticado · `404` cuenta destino no encontrada · `409` saldo insuficiente, cuenta origen bloqueada o cerrada, o cuenta destino cerrada |
| `GET /api/reportes/cuentas/{cuentaId}/movimientos` | HU-13 Historial de transacciones por cuenta | Lista los movimientos de una cuenta, con filtros de fecha/tipo y paginación | CLIENTE (solo las suyas), ADMIN, CAJERO (cualquiera) | `200` ok (puede venir vacío) · `400` rango de fechas, tipo o paginación inválidos · `401` sin token o token inválido · `403` cuenta ajena (CLIENTE) · `404` cuenta no encontrada (solo ADMIN/CAJERO) |

## Swagger

Las tres APIs usan springdoc y dejan la documentación sin token (`SecurityConfig` de cada API). En cada una, la interfaz está en `/swagger-ui.html` y el JSON de OpenAPI en `/v3/api-docs`.

## Documentación

- [docs/ADR](docs/ADR): decisiones de arquitectura del proyecto, una por archivo.
- [docs/riesgos-arquitectonicos.md](docs/riesgos-arquitectonicos.md): riesgos de la arquitectura actual.
- [docs/apis-priorizadas.md](docs/apis-priorizadas.md): endpoints con su prioridad y la razón.
