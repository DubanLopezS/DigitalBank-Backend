# ADR-0012: Pool de conexiones pequeño por el límite de Supabase y despliegue en Render free

**Fecha:** octubre de 2026
**Responsable:** Equipo Digital Bank Backend
**Estado:** Aceptado

**Nota:** este ADR se escribió el 25 de octubre de 2026, con la decisión ya tomada.

## Contexto

Las tres APIs corren en Render en el plan gratuito y se conectan a la misma base de Supabase (ADR-0009). Casi todo lo de este ADR es configuración de Render y Supabase, que no está en el repo; lo marcamos como dato del equipo. En el código solo se ve que ningún `application.properties` configura el pool de conexiones: las tres APIs usan lo que trae Spring Boot por defecto con Hikari, que es un máximo de 10 conexiones por API.

Dato del equipo: nos conectamos por el pooler de Supabase en modo sesión, que permite máximo 15 conexiones. Con las tres APIs arriba, cada una con hasta 10, nos pasamos del límite y el historial de movimientos de `reportes-api` empezó a responder 500 con el error `EMAXCONNSESSION`.

Dato del equipo: Render free apaga el servicio después de 15 minutos sin tráfico y tarda de 3 a 5 minutos en volver a levantarlo. Cada servicio tiene Auto-Deploy, así que al hacer merge a `main` Render construye y despliega de nuevo; ese es nuestro CD. En el repo sí está el CI (`.github/workflows/ci.yml`), que corre los tests y el análisis de SonarCloud en cada PR hacia `main` y en cada push a `main`, y los Dockerfile vuelven a correr los tests al construir la imagen.

## Alternativas que consideramos

- **Pooler en modo transacción**: cada conexión real a Postgres se comparte entre varios clientes y se libera al terminar cada transacción, así que el límite pesa menos. No lo consideramos porque el driver de Postgres prepara sentencias en el servidor y eso puede dar problemas en modo transacción, y habría que volver a probar la configuración.
- **Conexión directa a la base**: saltarse el pooler. Cambia host y puerto en los tres servicios y la base sigue teniendo su propio límite de conexiones en el plan gratuito (dato del equipo), así que no quita el problema de repartir conexiones entre tres APIs.
- **Plan de pago**: en Render el servicio no se apagaría y en Supabase habría más conexiones (dato del equipo). Lo descartamos por costo, en un proyecto de curso.
- **Bajar el pool de cada API**: dejar cada Hikari con pocas conexiones para que la suma de las tres quepa en el límite.

## Qué decidimos

Bajamos el pool. Dato del equipo: en los tres servicios de Render se pusieron las variables `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3` y `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=1`. Spring Boot las lee como `spring.datasource.hikari.maximum-pool-size` y `spring.datasource.hikari.minimum-idle`, así que no hubo que cambiar código. Con eso las tres APIs juntas abren como mucho 9 conexiones de las 15.

Seguimos en Render free con Auto-Deploy al hacer merge a `main`.

## Qué implica esto

**A favor:** no pagamos nada y las tres APIs caben en el límite con margen. El arreglo fue solo cambiar variables en Render. Cada merge a `main` queda desplegado sin pasos a mano.

**En contra:**

- Cada API atiende como mucho 3 consultas a la base al mismo tiempo. Si llegan más, esperan una conexión libre, y si la espera pasa el tiempo máximo de Hikari, la petición terminaría en 500, porque `GlobalExceptionHandler` no maneja errores de conexión.
- La configuración vive solo en Render. Quien corra las APIs en otro lado usa el valor por defecto de 10, y si se agrega una cuarta API o una segunda instancia de alguna, hay que volver a hacer la cuenta.
- Después de 15 minutos sin tráfico, la primera petición a cada API tarda de 3 a 5 minutos, y cada API se apaga por separado (dato del equipo). En una demo puede estar despierta `auth-api` y dormida `core-api`.
- Cada merge reinicia las APIs. `core-api` corre Flyway al arrancar, y si el merge trae una migración que cambia tablas de `cliente` o `usuario`, `auth-api` puede arrancar antes de que esté aplicada y fallar por `ddl-auto=validate`. Si cambia `cuenta` o `transaccion`, `reportes-api` arranca igual, pero sus consultas pueden fallar mientras tanto (ADR-0009).
