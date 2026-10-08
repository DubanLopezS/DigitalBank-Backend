# ADR-0007: Bloqueo de fila al mover el saldo de una cuenta

**Fecha:** 7 de octubre de 2026
**Responsable:** Equipo Digital Bank Backend

## Contexto

Tanto `acreditar` como `debitar` hacen lo mismo por dentro: leen la cuenta, le cambian el saldo y la guardan. Mientras solo había depósitos no le pusimos mucha atención, pero al empezar con retiros y transferencias nos dimos cuenta de que si dos operaciones llegan al mismo tiempo sobre la misma cuenta, las dos pueden leer el mismo saldo y la segunda en guardar pisa lo que hizo la primera. Lo único que nos protegía era el `CHECK (saldo_contable >= 0)` de la V1, y si ese saltaba, al cliente le iba a llegar un error de base de datos y no el 409 que piden las HU.

## Alternativas que consideramos

- **Bloqueo optimista con `@Version`**: Hibernate revisa al guardar si alguien más cambió la fila mientras tanto. La descartamos porque el conflicto se descubre al final, cuando ya se hizo todo el trabajo, y nos tocaba manejar reintentos en cada operación que mueva saldo.
- **Bloqueo pesimista con `SELECT ... FOR UPDATE`**: la fila de la cuenta queda bloqueada desde que se lee, y cualquier otra operación sobre esa misma cuenta espera a que la primera termine.
- **Dejar solo el `CHECK`**: no cambiar nada y confiar en la base de datos. La descartamos por lo mismo que contamos arriba: cuando actúa, responde con un error de base de datos y no con un 409.

## Qué decidimos

Nos fuimos por el bloqueo pesimista. A `ICuentaRepository` le agregamos `findByIdForUpdate`, y en `CuentaJpaRepository` lo volvimos a declarar con el `@Lock` y la consulta escrita a mano, para que la interfaz del módulo siga sin anotaciones de Spring. `acreditar` y `debitar` leen la cuenta con ese método, y como los dos son `@Transactional`, el bloqueo dura hasta que termina la transacción del servicio.

## Qué implica esto

**A favor:** dos movimientos sobre la misma cuenta ya no se pueden pisar, y las validaciones de estado y de saldo siempre trabajan con el saldo real. El `CHECK` de la V1 se queda, pero ahora como última red de seguridad y no como la única defensa.

**En contra:** la transferencia va a tener que bloquear dos cuentas a la vez, y si dos transferencias cruzadas las bloquean en distinto orden se pueden quedar esperando la una a la otra. Para evitar ese deadlock, la HU de transferencia tiene que bloquear las cuentas siempre en el mismo orden, por id. Y como las pruebas corren con H2, no prueban el bloqueo real de Postgres: lo que sí verificamos es que el servicio use `findByIdForUpdate` y no `findById`.
