# ADR-0008: Guardar una transferencia como dos filas ligadas

**Fecha:** 7 de octubre de 2026
**Responsable:** Equipo Digital Bank Backend

## Contexto

La tabla `transaccion` la armamos en el Sprint 1 pensando en operaciones sobre una sola cuenta: tiene un `cuenta_id` y un par `saldo_anterior` / `saldo_nuevo`. Para depósitos y retiros eso alcanza, pero una transferencia afecta dos cuentas al mismo tiempo, y cada una tiene su propio saldo antes y después, así que con una sola fila no había dónde dejar registrado lo que le pasó a las dos.

## Alternativas que consideramos

- **Una sola fila con los dos lados**: agregar `cuenta_origen_id`, `cuenta_destino_id` y los saldos de cada cuenta. Queda todo en un registro, pero la tabla se llena de columnas que en los depósitos y retiros van vacías, y para sacar el historial de una cuenta toca buscarla en varias columnas.
- **Dos filas ligadas por un `transferencia_id`**: una fila para la cuenta que envía y otra para la que recibe, cada una con su `cuenta_id` y sus saldos.

## Qué decidimos

Nos quedamos con las dos filas. Las dos llevan tipo `TRANSFERENCIA`, porque así lo nombra la HU-09a, y lo que las diferencia es el sentido: la de la cuenta origen va con `SALIENTE` y la de la cuenta destino con `ENTRANTE`. Las dos comparten el mismo `transferencia_id` y se guardan siempre dentro de la misma transacción que mueve los saldos. Cada fila se arma con el `MovimientoSaldoResponse` que ya devuelven `debitar` y `acreditar`. En la V3 agregamos `transferencia_id` con su índice y sin FK, porque no apunta a ninguna tabla, `sentido`, y `descripcion`, que la HU-09a pide como opcional. También agregamos un `CHECK` que obliga a que `transferencia_id` y `sentido` vengan llenos en las filas de transferencia y vacíos en las demás.

## Qué implica esto

**A favor:** el historial de una cuenta sale con un `WHERE cuenta_id = X` y nada más, sea depósito, retiro o transferencia, y cada fila tiene los saldos de su propia cuenta.

**En contra:** el criterio de la HU-09a habla de "un registro" de la transferencia y acá son dos filas, así que lo interpretamos como un registro lógico identificado por el `transferencia_id`. Como el tipo es el mismo en las dos, para saber si una fila de transferencia es un débito o un crédito hay que mirar `sentido`. Además, la base de datos no garantiza que estén las dos filas, porque el `CHECK` revisa cada una por separado, entonces siempre hay que insertarlas juntas desde el servicio y la HU-09 tiene que tener un test que lo verifique.
