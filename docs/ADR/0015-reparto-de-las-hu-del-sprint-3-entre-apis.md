# ADR-0015: Qué API implementa cada HU del Sprint 3 y qué permisos de base de datos se amplían

**Fecha:** noviembre de 2026
**Responsable:** Equipo Digital Bank Backend
**Estado:** Aceptado

**Nota:** este ADR se escribió el 17 de noviembre de 2026, antes de terminar el Sprint 3. Las HU-02, HU-03, HU-08 y HU-14 ya estaban implementadas o en revisión; las HU-04 y HU-12 todavía no.

## Contexto

El Sprint 3 cierra el backlog con seis historias de usuario: HU-02 consulta de información del cliente, HU-03 actualización de datos del cliente, HU-04 inactivación de cliente, HU-08 listado de cuentas por cliente, HU-12 reversión de transacción y HU-14 detalle de una transacción.

Según el ADR-0009, cada API es dueña de sus tablas: `auth-api` de `cliente` y `usuario`, `core-api` de `cuenta`, `transaccion` y `movimiento_administrativo_cuenta`, y `reportes-api` solo lee. El script `04-roles-seguridad.sql` del entregable de Bases de Datos refuerza esa separación con un rol de base de datos por API y permisos por columna. Varias de las HU nuevas necesitan datos de dos dueños a la vez, así que había que decidir en qué API va cada una y qué permisos se abren, antes de que cada quien lo resolviera a su manera.

La migración V6 ya creó lo que estas HU necesitan en la base: las tablas `auditoria_datos_cliente` y `movimiento_administrativo_cliente`, y en `transaccion` las columnas `usuario_id`, `motivo` y `transaccion_relacionada_id`, junto con el tipo REVERSO y el estado REVERSADA.

## Qué decidimos

| HU | API | Permisos nuevos en la base |
|---|---|---|
| HU-02 Consulta de información del cliente | `auth-api` | `SELECT (cliente_id, numero_cuenta, tipo_cuenta, estado)` sobre `cuenta`, sin saldos |
| HU-03 Actualización de datos del cliente | `auth-api` | `UPDATE (email, telefono)` sobre `cliente` e `INSERT` sobre `auditoria_datos_cliente` |
| HU-04 Inactivación de cliente | `core-api` | `UPDATE (estado)` sobre `cliente` e `INSERT` sobre `movimiento_administrativo_cliente` |
| HU-08 Listado de cuentas por cliente | `core-api` | Ninguno |
| HU-12 Reversión de transacción | `core-api` | `SELECT` sobre `transaccion` y `UPDATE (estado, transaccion_relacionada_id)` sobre `transaccion` |
| HU-14 Detalle de una transacción | `core-api` guarda el usuario de cada transacción y `reportes-api` hace la consulta | Ninguno |

Cada permiso nuevo va con su política RLS para ese rol, igual que las que ya tiene el script 04. Por ejemplo, la HU-03 necesita una política de `INSERT` para `digitalbank_auth` en `auditoria_datos_cliente`, porque con RLS activo el permiso solo no alcanza.

Las tres consultas nuevas siguen la regla del ADR-0005: a un CLIENTE que pide un recurso ajeno o que no existe se le responde 403, y el 404 queda para el personal del banco.

## Por qué cada una va donde va

**HU-02 en `auth-api` y no en `core-api`.** La respuesta lleva los datos personales completos del cliente: nombres, correo, teléfono y fecha de nacimiento. Hoy `core-api` solo puede leer el id, el documento y el estado del cliente, y el script `05-prueba-permisos.sql` tiene una prueba que exige que no pueda leer datos personales. Ponerla en `core-api` obligaba a abrir esos datos. En `auth-api`, que ya es dueña del cliente, solo hay que abrir cuatro columnas de `cuenta` que no son sensibles.

La alternativa era `core-api`, porque así `auth-api` no conoce cuentas y la consulta del resumen se reutiliza con la HU-08. La descartamos porque abrir datos personales es más delicado que abrir número, tipo y estado de una cuenta.

**HU-03 en `auth-api`.** Cambia el correo y el teléfono del cliente y también el correo del usuario, para que el login use el nuevo. Las dos tablas son de `auth-api`, así que no hay cruce. La entidad `Cliente` usa `@DynamicUpdate` para que el UPDATE solo lleve las columnas que cambian y alcance con el permiso por columna.

**HU-04 en `core-api`.** La inactivación revisa que todas las cuentas del cliente tengan saldo $0, las pasa a BLOQUEADA y cambia el estado del cliente, todo en una sola transacción. Las cuentas y los saldos ya son de `core-api`, que además ya lee el estado del cliente. Solo hay que abrirle la columna `estado` de `cliente` para escritura, sin datos personales.

La alternativa era `auth-api`, por ser dueña del cliente. La descartamos porque tendría que leer saldos y actualizar cuentas, que es más sensible y rompe más la separación del ADR-0009. Llamar de una API a la otra por HTTP tampoco servía, porque la operación se podía quedar a medias.

**HU-08 en `core-api`.** Lista cuentas con su saldo, y las cuentas son de `core-api`. No abre nada.

**HU-12 en `core-api`.** El reverso mueve saldos con el mismo bloqueo de filas de los depósitos, retiros y transferencias, y eso vive en `core-api`. Necesita leer la transacción original y cambiarle el estado a REVERSADA, cosa que hoy no puede hacer, porque `core-api` solo inserta en `transaccion`.

Esto cambia una regla del entregable de Bases de Datos, que dice que las transacciones no se modifican. La excepción es mínima: solo se pueden cambiar `estado` y `transaccion_relacionada_id`, nunca el monto ni los saldos, y el registro original no se borra. La entidad `Transaccion` debe usar `@DynamicUpdate` por la misma razón que `Cliente`.

**HU-14 entre `core-api` y `reportes-api`.** Para mostrar quién originó una transacción, `core-api` guarda el `usuario_id` en cada depósito, retiro y transferencia, con el permiso de `INSERT` que ya tiene. La consulta va en `reportes-api`, que ya lee toda `transaccion` y de `cuenta` solo `id`, `cliente_id` y `numero_cuenta`. No abre nada.

## Qué implica

- A favor: cada HU queda en la API que ya tiene la mayoría de sus datos, y ningún rol gana acceso a datos personales o saldos que no tenía.
- A favor: los permisos nuevos son por columna, así que siguen la idea de mínimo privilegio del entregable de Bases de Datos.
- En contra: `auth-api` ahora conoce la tabla `cuenta` y `core-api` puede cambiar el estado de un cliente. Son acoplamientos nuevos entre APIs, como el que ya aceptó el ADR-0009 cuando `core-api` empezó a leer clientes.
- En contra: el resumen de cuentas queda escrito en dos APIs, en la HU-02 y en la HU-08.
- En contra: las transacciones dejan de ser totalmente inmutables, aunque solo en dos columnas.
- El 27 de noviembre de 2026 se actualizaron `04-roles-seguridad.sql` y `05-prueba-permisos.sql` del entregable de Bases de Datos con estos permisos y sus políticas, y quedaron en `docs/scripts` junto con `02-datos-prueba.sql`.
