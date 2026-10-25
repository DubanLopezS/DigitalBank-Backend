# ADR-0010: JWT firmado con HMAC y un secreto compartido entre las tres APIs

**Fecha:** 8 de octubre de 2026
**Responsable:** Equipo Digital Bank Backend
**Estado:** Aceptado

**Nota:** este ADR se escribió el 25 de octubre de 2026, con la decisión ya tomada.

## Contexto

El login (HU-18) devuelve un JWT y todas las demás operaciones lo piden en el header `Authorization`. El login con el JWT firmado con HMAC lo hicimos en el Sprint 1 (15 de septiembre de 2026), cuando había un solo proceso, y no había quedado en un ADR. Con la separación en tres APIs (ADR-0009, 8 de octubre de 2026) el token lo sigue creando `auth-api`, y `core-api` pasó a validarlo con el mismo secreto, sin llamar a `auth-api`. `reportes-api` se sumó después, con la HU-13. Así que las tres necesitan una clave para verificar la firma.

Hoy el token lo arma `JwtServiceImpl` de `auth-api`: el `subject` es el id del usuario, lleva los claims `email` y `rol`, y `clienteId` solo cuando el usuario es un cliente. Vence a los 60 minutos (`security.jwt.expiration-minutes=60` en `auth-api/src/main/resources/application.properties`).

## Alternativas que consideramos

- **Firma asimétrica (RS256 o similar)**: `auth-api` firma con una clave privada y `core-api` y `reportes-api` verifican con la pública. Así solo `auth-api` puede crear tokens. La descartamos por ahora. En el Sprint 1, con un solo proceso, no daba ninguna ventaja. Al separar en tres APIs habría que generar y guardar el par de claves, pasar la pública a las otras dos y cambiar las tres `JwtServiceImpl`.
- **Delegar la autenticación a un proveedor externo**: que el login y los tokens los maneje un servicio aparte. La descartamos porque las reglas del login de la HU-18 (tres intentos fallidos y bloqueo de una hora, en `Usuario`) y los roles CLIENTE, ADMIN y CAJERO ya están en nuestra tabla `usuario`, y moverlos a otro servicio no cabía en el sprint.
- **HMAC con un secreto compartido**: las tres APIs leen el mismo `JWT_SECRET` y lo usan para firmar (solo `auth-api`) y verificar.

## Qué decidimos

Nos quedamos con HMAC. En el Sprint 1 el único proceso firmaba y verificaba el token con un solo secreto. Con el ADR-0009 las tres APIs quedaron validando con el mismo secreto. Las tres `JwtServiceImpl` arman la clave con `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))` a partir de `security.jwt.secret=${JWT_SECRET}`. `auth-api` firma con `signWith(key)` y las tres verifican con `verifyWith(key)` en `parseToken`. El filtro `JwtAuthenticationFilter` solo revisa que la firma sea válida y que el token no esté vencido; si es así, pone el id de usuario como principal y el rol como `ROLE_<rol>`, y en `core-api` y `reportes-api` también el `clienteId` en los `details`.

La interfaz de `core-api` y `reportes-api` no tiene `generarToken`, así que en el código solo `auth-api` crea tokens.

## Qué implica esto

**A favor:** validar un token no necesita red ni base de datos, ninguna API depende de que `auth-api` esté despierta para atender una petición, y con jjwt es poco código. Si el secreto tiene menos de 256 bits, `Keys.hmacShaKeyFor` lanza una excepción y la API no arrancaría, así que un secreto demasiado corto no pasaría desapercibido; ningún test cubre un secreto corto.

**En contra:**

- Cualquier API que tenga el secreto puede firmar tokens válidos con cualquier rol. Que `core-api` y `reportes-api` no tengan `generarToken` depende solo de cómo escribimos el código; con el secreto podrían firmar igual. Si alguien consigue el `JWT_SECRET` de cualquiera de los tres servicios, puede hacerse pasar por ADMIN en todo el sistema.
- Un token no se puede revocar antes de que venza. Hasta que vence, un usuario bloqueado o un cliente que pase a INACTIVO sigue entrando, y si a alguien le cambian el rol en la base, el token sigue diciendo el rol viejo. Lo mitiga en parte que las operaciones leen el estado de la cuenta en la base, pero `RetiroServiceImpl` y `TransferenciaServiceImpl` no revisan el estado del cliente.
- Cambiar el secreto invalida todos los tokens a la vez, y hay que cambiarlo en los tres servicios al mismo tiempo; si no, una API rechaza los tokens que la otra acepta.
- El token no va cifrado, solo firmado: el `email`, el `rol` y el `clienteId` se pueden leer decodificándolo.
