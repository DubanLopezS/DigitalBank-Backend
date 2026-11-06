# ADR-0013: Rutas versionadas y cuerpo de error único con errorCode y traceId

**Fecha:** noviembre de 2026
**Responsable:** Equipo Digital Bank Backend
**Estado:** Aceptado

**Nota:** este ADR se escribió el 4 de noviembre de 2026, para cerrar el hallazgo HZ-10 de Calidad. 

**Nota de actualización (6 de noviembre de 2026):** después de este ADR, el `SecurityConfig` de las tres APIs se movió a `common` (corrección por la duplicación que marcó SonarCloud). Corregimos lo que decía de que seguía repetido.

## Contexto

Calidad reportó que las rutas no están versionadas y que el cuerpo de error no incluye `errorCode` ni `traceId`.

Antes de este cambio:

- Las rutas eran `/api/auth`, `/api/clientes`, `/api/cuentas` y `/api/reportes/cuentas`, sin versión.
- El error de negocio traía `timestamp`, `status`, `error`, `message` y `path`.
- Los errores 401 y 403 se escribían a mano dentro del `SecurityConfig` de cada API. Eran tres copias del mismo texto, sin `timestamp` y con el mensaje del 403 distinto en `auth-api`.
- Los errores que Spring genera solo (ruta inexistente, método incorrecto, fecha o UUID mal escrito) salían con el cuerpo por defecto de Spring.

## Alternativas que consideramos

- **Versión en un header o en un parámetro**: no cambia la ruta, pero no se ve en Swagger ni en Postman a simple vista y es más fácil olvidarla. Elegimos la versión en la ruta.
- **errorCode derivado del nombre de la clase**: toca menos archivos, pero si alguien renombra la clase el código cambia sin que nadie lo note, y el código existe justamente para no cambiar. Elegimos escribirlo de forma explícita.
- **traceId solo si lo manda el cliente**: no sirve cuando el cliente no lo manda. Elegimos respetarlo si llega y generarlo si no.
- **Dejar los handlers 401/403 en cada SecurityConfig**: obliga a repetir el cambio en tres lugares. Elegimos moverlos a `common`.

## Qué decidimos

**Rutas.** Las tres APIs usan el prefijo `/api/v1`: `/api/v1/auth`, `/api/v1/clientes`, `/api/v1/cuentas` y `/api/v1/reportes/cuentas`. Swagger (`/swagger-ui.html`, `/v3/api-docs`) no se versiona.

**errorCode.** Es un texto en mayúsculas con guiones bajos. `ApiException` lo recibe de forma obligatoria: `super(HttpStatus, errorCode, mensaje)`. Así el compilador avisa si una excepción nueva lo olvida. Una vez publicado, un código no se cambia, aunque el mensaje sí pueda cambiar. Los códigos que no vienen de una excepción de negocio son fijos:

| Caso | Estado | errorCode |
|---|---|---|
| Sin token o token inválido | 401 | `NO_AUTENTICADO` |
| Rol sin permiso | 403 | `ACCESO_NO_AUTORIZADO` |
| Validación de campos | 400 | `DATOS_INVALIDOS` |
| JSON ilegible | 400 | `SOLICITUD_INVALIDA` |
| Fecha, UUID u otro parámetro mal escrito | 400 | `PARAMETRO_INVALIDO` |
| Falta un parámetro obligatorio | 400 | `PARAMETRO_REQUERIDO` |
| Ruta inexistente | 404 | `RECURSO_NO_ENCONTRADO` |
| Método incorrecto | 405 | `METODO_NO_PERMITIDO` |
| Tipo de contenido no soportado | 415 | `TIPO_CONTENIDO_NO_SOPORTADO` |
| Error inesperado | 500 | `ERROR_INTERNO` |

**traceId.** Un filtro (`TraceIdFilter`, en `common`) corre antes que Spring Security. Toma el header `X-Trace-Id` si trae de 8 a 64 caracteres entre letras, números y guiones. Si no, genera un UUID. Lo guarda en el MDC de los logs, lo devuelve en el header de la respuesta y lo incluye en el cuerpo de todo error. El patrón de logs (`logging.pattern.level`) lo muestra en cada línea. El formato se valida para que un cliente no pueda meter saltos de línea y falsificar líneas en los logs.

**Cuerpo de error.** Todos los errores llevan `timestamp`, `status`, `error`, `errorCode`, `message`, `path` y `traceId`. Los 401 y 403 salen de `ApiAuthenticationEntryPoint` y `ApiAccessDeniedHandler`, que usan `ErrorResponseWriter` (los tres en `common`). El resto sale de `GlobalExceptionHandler`. El handler genérico de `Exception` devuelve 500 sin el mensaje de la excepción, y el detalle va solo al log junto con el `traceId`. Los errores de seguridad (`AccessDeniedException`, `AuthenticationException`) se relanzan para no convertirlos en 500.

## Qué implica esto

**A favor:** todo error de las tres APIs tiene el mismo formato. Un programa puede decidir por `errorCode` sin depender del texto. Si Calidad reporta un fallo, con el `traceId` se buscan sus líneas en los logs. Cambiar el contrato en el futuro se resuelve con `/v2` sin romper a quien use `/v1`.

**En contra:**

- Las rutas sin versión ahora dan 404. Postman, las pruebas de Calidad y cualquier documentación externa deben actualizarse.
- El `errorCode` pasa a ser parte del contrato. Cambiarlo rompe a quien lo use, y toda excepción nueva debe declarar uno.
- El `traceId` solo aparece en los logs si la petición escribe alguna línea. Hoy `GlobalExceptionHandler` registra los 409 y los 500, así que un 401, 403, 404 o 400 muestra el `traceId` en la respuesta pero no deja línea de log.
- El `SecurityConfig` ya no está repetido: hay uno solo en `common`, que recibe el filtro JWT de cada API a través de la interfaz `FiltroAutenticacionJwt`. Las rutas públicas propias de una API se declaran con la propiedad `seguridad.rutas-publicas` (hoy solo `auth-api`, con `/api/v1/auth/login`). Por eso `common` ya no tiene solo excepciones y errores: también tiene la configuración de seguridad, y toda API nueva que use `common` debe tener un filtro que implemente `FiltroAutenticacionJwt`, o no arranca.
- Los tests `@WebMvcTest` que importan `SecurityConfig` deben importar también `ApiAuthenticationEntryPoint`, `ApiAccessDeniedHandler` y `ErrorResponseWriter`, o el contexto no arranca.
- Los errores de conexión a la base (ADR-0012) ahora salen como 500 con `ERROR_INTERNO` y quedan registrados con su `traceId`. Antes no quedaban claros en el log.
- Una ruta inexistente sin token responde 401 y no 404, porque la seguridad actúa primero.