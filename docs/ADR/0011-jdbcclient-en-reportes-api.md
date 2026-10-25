# ADR-0011: JdbcClient en vez de JPA para las consultas de `reportes-api`

**Fecha:** 18 de octubre de 2026
**Responsable:** Equipo Digital Bank Backend
**Estado:** Aceptado

**Nota:** este ADR se escribió el 25 de octubre de 2026, con la decisión ya tomada.

## Contexto

`reportes-api` es de solo lectura y su primer endpoint es el historial de movimientos de una cuenta (HU-13). La consulta tiene filtros opcionales (`fecha_inicio`, `fecha_fin` y `tipo`), paginación, y para cada transferencia tiene que traer el número de la otra cuenta. Eso último sale de buscar la otra fila con el mismo `transferencia_id` (ADR-0008) y luego su cuenta, o sea un join de `transaccion` consigo misma y con `cuenta`.

Las tablas son de `core-api`: ahí están las migraciones y las entidades `Cuenta` y `Transaccion`. `reportes-api` no tiene esas clases, y por el ADR-0009 no las puede pedir por HTTP. En `core-api` ya habíamos tenido el mismo caso con la tabla `cliente`, que es de `auth-api`, y lo resolvimos con `JdbcClient` en `ClienteConsultaAdapter`.

## Alternativas que consideramos

- **JPA**: copiar `Cuenta` y `Transaccion` como entidades en `reportes-api` y armar la consulta con JPQL o con `Specification` para los filtros opcionales. Con `ddl-auto=validate`, la API no arrancaría si el esquema no coincide con las entidades, lo cual sería una ventaja. La descartamos porque toca mantener una tercera copia de las entidades de `core-api` (ya hay clases repetidas, ADR-0009), meter Hibernate en una API que nunca escribe, y el resultado igual hay que pasarlo a un DTO.
- **`JdbcClient` de Spring**: SQL escrito a mano, parámetros con nombre y el mapeo de cada fila a un `record`.

## Qué decidimos

Nos fuimos por `JdbcClient`. El `pom.xml` de `reportes-api` trae `spring-boot-starter-jdbc` y no `spring-boot-starter-data-jpa`. `MovimientoJdbcRepository` implementa `IMovimientoRepository` con tres consultas: buscar el `cliente_id` de la cuenta (para la regla del ADR-0005), traer la página de movimientos y contarlos. El `WHERE` se arma solo con pedazos de texto fijos según qué filtros llegan, y los valores siempre van como parámetros con nombre, así que no se concatena nada que venga del usuario. Cada fila se pasa a `MovimientoResponse` en `mapearMovimiento`. `MovimientoServiceImpl` valida los filtros antes de consultar y corre con `@Transactional(readOnly = true)`.

## Qué implica esto

**A favor:** no hay que copiar entidades de `core-api`, la consulta que corre es exactamente la que está escrita, el join para la cuenta relacionada queda en una sola sentencia y `reportes-api` arranca sin Hibernate. Es el mismo patrón que ya usaba `ClienteConsultaAdapter`.

**En contra:**

- El SQL está acoplado a los nombres de tablas y columnas de `core-api` y nada lo revisa al compilar ni al arrancar, porque `reportes-api` no tiene `ddl-auto=validate`. Si una migración de `core-api` renombra una columna, el historial fallaría con 500 en la primera consulta; ningún test cubre ese caso.
- Los tests de `reportes-api` corren sobre `src/test/resources/sql/esquema-movimientos.sql`, que es una copia a mano de las tablas `cuenta` y `transaccion` de las migraciones V1 a V3. Si cambia una migración y nadie actualiza la copia, los tests siguen pasando con el esquema viejo.
- El mapeo es manual: una columna nueva hay que agregarla en el `SELECT`, en `mapearMovimiento` y en `MovimientoResponse`.
- Las reglas de valores permitidos quedan en dos sitios que no se hablan. `TIPOS_VALIDOS` en `MovimientoServiceImpl` acepta `REVERSO`, que no existe ni en `TipoTransaccion` ni en el `CHECK` de la V3. Pedir `tipo=REVERSO` responde 200 con una lista vacía. `MovimientoServiceImplTest` lo trata como un tipo válido a propósito, pero ni el enum ni el `CHECK` lo tienen, así que cuando llegue toca agregarlo en los dos lados.
