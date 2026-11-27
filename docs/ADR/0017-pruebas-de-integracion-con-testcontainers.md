# ADR-0017: Pruebas de integración contra PostgreSQL real con Testcontainers

**Fecha:** noviembre de 2026
**Responsable:** Equipo Digital Bank Backend
**Estado:** Aceptado

**Nota:** este ADR se escribió el 27 de noviembre de 2026, con la decisión ya tomada. Las pruebas sobre PostgreSQL entraron el 25 de noviembre de 2026, y el 26 se agregó la última prueba de los triggers de la V7.

## Contexto

Las pruebas de `core-api` usan `core-api/src/test/resources/application.properties`, que reemplaza la configuración normal: la base es H2 en memoria, `spring.flyway.enabled=false` y `spring.jpa.hibernate.ddl-auto=create-drop`. El mismo archivo dice que las migraciones de Flyway son específicas de PostgreSQL. Con eso las tablas de las pruebas las crea Hibernate a partir de las entidades, no las migraciones de `db/migration`, y había cosas que ninguna prueba tocaba:

- **Las migraciones.** Ninguna prueba corría la V1 a la V7. La V5, por ejemplo, tiene una guarda que lanza `RAISE EXCEPTION 'V5 detenida: ...'` si hay correos que solo se diferencian en mayúsculas o espacios, y eso solo existe en PostgreSQL.
- **Los CHECK y los UNIQUE con nombre.** Las entidades no declaran restricciones únicas, así que en H2 no existen `uk_transaccion_relacionada`, `uk_cuenta_numero` ni los CHECK de las migraciones, como `ck_cuenta_saldo_no_negativo`, `ck_transaccion_reverso`, que le exige a un REVERSO usuario, transacción original y un motivo de al menos 10 caracteres sin contar los espacios de los extremos, o `ck_cliente_email_minusculas` de la V5.
- **Los triggers de la V7.** Están escritos en PL/pgSQL: impiden editar o borrar las tablas de auditoría, impiden borrar transacciones, solo dejan pasar una transacción de COMPLETADA a REVERSADA enlazada a su propio REVERSO, y no dejan crear cuentas para un cliente INACTIVO. En H2 no existen.
- **Los bloqueos de fila.** Depósitos, retiros, transferencias y reversos bloquean filas con `@Lock(LockModeType.PESSIMISTIC_WRITE)` en `CuentaJpaRepository` y `TransaccionJpaRepository` (ADR-0007), y `ClienteEstadoService` usa `SELECT ... FOR UPDATE`. Las pruebas unitarias como `ReversoServiceImplTest` usan mocks del repositorio, así que nunca había dos transacciones reales compitiendo por la misma fila.
- **`ddl-auto=validate`.** En producción `core-api` arranca con `validate`, pero en las pruebas Hibernate crea el esquema a su medida, así que si una entidad no cuadraba con las migraciones, se veía al desplegar.

## Alternativas que consideramos

- **Seguir solo con H2**: es rápido y no necesita nada instalado. Lo descartamos porque deja sin probar justo lo que la base hace por su cuenta, y desde la HU-12 y la V7 esa parte creció: reversos, estado REVERSADA y triggers.
- **H2 en modo compatibilidad PostgreSQL**: `MODE=PostgreSQL` acepta parte de la sintaxis, y de hecho las pruebas de `reportes-api` ya lo usan para sus consultas de lectura. Para `core-api` no alcanza: los triggers de H2 son clases Java y no PL/pgSQL, así que la V7 y la guarda de la V5 no se podrían correr. Tampoco los bloqueos ni los mensajes de error serían los de PostgreSQL.
- **Una base PostgreSQL compartida para pruebas, por ejemplo la de Supabase**: sería PostgreSQL de verdad, pero `IntegracionPostgresBase` hace `TRUNCATE` de todas las tablas antes de cada prueba, así que no se puede correr contra una base con datos que alguien más use. Además, el pooler de Supabase permite 15 conexiones y ya las comparten las tres APIs (ADR-0012), el CI tendría que guardar credenciales de la base, y dos PR corriendo a la vez se borrarían los datos entre ellos.
- **Embedded postgres**: una librería que descarga y corre los binarios de PostgreSQL sin Docker. La descartamos porque queríamos la misma imagen `postgres:16` del `docker-compose.yml`, como dice el comentario de `PostgresTestcontainersConfig`, y porque Spring Boot ya trae soporte para Testcontainers con `spring-boot-testcontainers` y `@ServiceConnection`, sin configurar la URL a mano.

## Qué decidimos

Agregamos pruebas de integración en `core-api/src/test/java/com/fabricaescuela/digitalbank/integracion` que corren contra un PostgreSQL real levantado con Testcontainers. Las pruebas sobre H2 siguen igual.

`core-api/pom.xml` agrega en `test` las dependencias `spring-boot-testcontainers`, `testcontainers-postgresql` y `testcontainers-junit-jupiter`. Las clases quedaron así:

- **`PostgresTestcontainersConfig`**: un `@TestConfiguration` con un bean `PostgreSQLContainer` de la imagen `postgres:16` marcado con `@ServiceConnection`, así Spring toma la URL, el usuario y la clave del contenedor. El contenedor vive con el contexto de Spring, y las clases que comparten contexto reutilizan el mismo PostgreSQL.
- **`IntegracionPostgresBase`**: la clase base abstracta. Es un `@SpringBootTest` que enciende `spring.flyway.enabled=true`, pone `spring.jpa.hibernate.ddl-auto=validate` y el dialecto y driver de PostgreSQL, importa `PostgresTestcontainersConfig` y tiene `@Testcontainers(disabledWithoutDocker = true)`. Antes de cada prueba hace `TRUNCATE ... CASCADE` de todas las tablas del esquema `public` menos `flyway_schema_history`, incluidas las que agreguen migraciones futuras. También trae ayudas para insertar clientes y cuentas y leer saldos.
- **`MigracionesPostgresIntegrationTest`**: prueba que Flyway aplica todas las migraciones sin fallos, que el contexto arranca con `ddl-auto=validate`, que existen los CHECK, UNIQUE y FK que definen las migraciones, y que la guarda de la V5 se detiene con correos repetidos en mayúsculas sin borrar nada.
- **`RestriccionesPostgresIntegrationTest`**: prueba con filas inválidas que PostgreSQL las rechaza con la restricción exacta que se espera, y con filas válidas que las acepta, por ejemplo dos usuarios ADMIN sin cliente o las dos piernas de una transferencia.
- **`ReversoPostgresIntegrationTest`**: prueba el reverso de la HU-12 de punta a punta: un depósito, una transferencia, el rollback cuando el destino ya no tiene saldo, el cliente inactivo, un segundo reverso que llega después de que el primero ya consultó el tipo, y dos reversos simultáneos en dos hilos, repetidos 10 veces, donde uno se aplica y el otro recibe 409.
- **`TriggersPostgresIntegrationTest`**: prueba que la V7 crea los seis triggers y que cada uno hace lo suyo: las tablas de auditoría rechazan UPDATE y DELETE, una transacción no se borra ni se modifica, solo pasa de COMPLETADA a REVERSADA enlazada a su propio reverso, y un cliente INACTIVO no recibe cuentas nuevas.

La prueba de migraciones no tiene la lista de versiones escrita a mano. Lee con `PathMatchingResourcePatternResolver` todos los archivos `classpath:db/migration/V*__*.sql`, saca el número de versión de cada nombre y compara esa lista con las versiones aplicadas con éxito en `flyway_schema_history`. Así cada migración nueva queda cubierta sola, sin tocar la prueba.

### Lo que encontraron

El commit `75c113b` del 25 de noviembre de 2026 corrige que un reverso concurrente de la HU-12 respondía 500 en vez de 409, y su mensaje dice que se detectó con las pruebas sobre PostgreSQL. Según ese commit, `reversar()` cargaba la transacción sin bloqueo para saber su tipo. Hibernate la dejaba en la sesión, y el `SELECT ... FOR UPDATE` que venía después devolvía esa misma copia sin refrescarla, así que no veía un reverso que otra petición había confirmado en medio. Seguía adelante, chocaba con `uk_transaccion_relacionada` y la API respondía 500. El arreglo fue consultar el tipo con una proyección, `ResumenTransaccion`, que no carga la entidad, para que el bloqueo siempre lea el estado real de la base.

## Qué implica esto

**A favor:** las migraciones, las restricciones, los triggers y los bloqueos de fila ahora tienen pruebas, y corren contra la misma versión de PostgreSQL del Compose. Si una entidad deja de cuadrar con el esquema, falla una prueba y no el despliegue. Las pruebas de concurrencia del reverso ya encontraron un bug real. Las pruebas de H2, que son las rápidas, siguen como estaban.

**En contra:**

- Necesitan Docker. Por `disabledWithoutDocker = true`, sin Docker las pruebas de esta carpeta se saltan en vez de fallar, y el build sale verde igual. Hay que revisar en el log del CI que hayan corrido y no que aparezcan como saltadas.
- Los tres Dockerfile corren `./mvnw -pl <api> -am clean package -B`, que también ejecuta las pruebas desde el commit `79c60ac`, que quitó `-DskipTests`. Dentro del `docker build` no hay Docker, así que ahí las pruebas de Testcontainers se saltan. Como Render construye con esos mismos Dockerfile, en Render también se saltan. Solo corren de verdad en el CI, que en `.github/workflows/ci.yml` corre `./mvnw -B clean verify` en `ubuntu-latest`. Las máquinas `ubuntu-latest` de GitHub Actions traen Docker, y por eso ahí sí corren.
- El CI tarda más: tiene que bajar la imagen `postgres:16`, arrancar el contenedor y un contexto de Spring con Flyway, y la prueba de reversos simultáneos se repite 10 veces.
- Solo `core-api` tiene estas pruebas. `auth-api` y `reportes-api` no tienen Testcontainers en su `pom.xml`, así que sus consultas siguen probándose solo sobre H2: en `auth-api` con Flyway apagado, y en `reportes-api`, incluidas las de JdbcClient, con H2 en `MODE=PostgreSQL`.
