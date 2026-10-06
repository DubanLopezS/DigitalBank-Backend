# ADR-0006: Sacar las transacciones de `cuenta` a un módulo propio

**Fecha:** 3 de octubre de 2026
**Responsable:** Equipo Digital Bank Backend

## Contexto

Cuando armamos la estructura del ADR-0001 dejamos las transacciones viviendo dentro del módulo `cuenta`. En ese momento tenía sentido: la única operación que movía plata era el depósito, y un depósito siempre afecta a una sola cuenta, así que pensábamos en la transacción como algo que "le pertenece" a una cuenta, igual que su saldo o su estado. Por eso `Transaccion`, su repositorio, `DepositoRequest`, `TransaccionResponse` y `DepositoServiceImpl` quedaron todos metidos en `cuenta/`.

El problema apareció al empezar a mirar las transferencias, que son la siguiente HU. Una transferencia toca dos cuentas al mismo tiempo (una a la que se le debita y otra a la que se le acredita), y ahí el modelo de "una transacción le pertenece a una cuenta" ya no encaja: ¿de cuál de las dos cuentas es la transferencia? Si seguíamos con todo dentro de `cuenta`, la lógica de mover plata entre cuentas iba a terminar mezclada con la lógica de abrir cuentas y consultar saldos, que es justo lo que queríamos evitar con el monolito modular.

Además, revisando el código nos dimos cuenta de que `DepositoServiceImpl` ni siquiera pasaba por la interfaz de servicio de cuentas: buscaba la `Cuenta` con `ICuentaRepository`, llamaba directamente a `cuenta.acreditar(...)` y la guardaba. Como estaba en el mismo módulo no rompía la regla técnicamente, pero en cuanto quisiéramos separarlo se iba a notar. Y de paso teníamos pendiente la deuda que dejamos anotada en el ADR-0001: `CuentaServiceImpl` importaba `Cliente`, `EstadoCliente` e `IClienteRepository` directamente del módulo `cliente`.

## Alternativas que consideramos

- **Dejar todo en `cuenta` y solo limpiar el depósito**: era lo menos trabajoso, pero no resolvía nada de fondo. Las transferencias iban a seguir sin tener un lugar natural y `cuenta` iba a seguir creciendo con responsabilidades que no son suyas.
- **Crear el módulo `transaccion` pero dejar que use `ICuentaRepository` y la entidad `Cuenta` directamente**: así el depósito quedaba igual que antes, solo que en otro paquete. La descartamos porque era cambiar de carpeta sin arreglar el acople, y rompía la regla de módulos que nos pusimos en el ADR-0001.
- **Crear el módulo `transaccion` y que hable con `cuenta` solo a través de `ICuentaService`**: `cuenta` sigue siendo la dueña del saldo y de sus reglas (si existe, si está cerrada), y `transaccion` solo le pide que acredite y registra el movimiento con lo que le devuelven.

## Qué decidimos

Nos fuimos por la tercera opción. Creamos el módulo `transaccion` con sus capas `dto`, `entity`, `interfaces`, `repository`, `service` y `controller`, y movimos ahí todo lo que era de transacciones: `Transaccion` junto con sus enums (`TipoTransaccion`, `EstadoTransaccion`, `OrigenDeposito`), `ITransaccionRepository`, `TransaccionJpaRepository`, `IDepositoService`, `DepositoServiceImpl`, `DepositoRequest` y `TransaccionResponse`. No le pusimos paquete `exception` porque por ahora no tiene excepciones propias.

A `ICuentaService` le agregamos `acreditar(UUID cuentaId, BigDecimal monto)`, que devuelve un `MovimientoSaldoResponse` con el id de la cuenta, el saldo anterior y el saldo nuevo. Toda la validación sigue en `CuentaServiceImpl`: si la cuenta no existe lanza `CuentaNoEncontradaException` y si está cerrada lanza `CuentaNoDisponibleException`, igual que antes. Esas excepciones se quedaron en `cuenta/exception`, porque son reglas de la cuenta y no de la transacción. `DepositoServiceImpl` ahora solo recibe `ICuentaService` e `ITransaccionRepository`, llama a `acreditar` dentro del mismo método `@Transactional` y arma la `Transaccion` con los saldos que le devuelven. Si `acreditar` lanza una excepción, la dejamos subir tal cual.

El endpoint de depósitos lo pasamos a un `DepositoController` dentro de `transaccion`, pero con la misma ruta de siempre (`POST /api/cuentas/{cuentaId}/depositos`), así que para el que consume la API no cambió nada. Al principio pensamos dejarlo en `CuentaController`, pero eso hacía que `cuenta` importara cosas de `transaccion` mientras `transaccion` dependía de `cuenta`, o sea, una dependencia circular. Con el controller en su propio módulo la dependencia va en una sola dirección: `transaccion` conoce a `cuenta`, y `cuenta` no sabe que `transaccion` existe.

Aprovechando el mismo cambio cerramos la deuda del ADR-0001 con `cliente`. Le agregamos a `IClienteService` el método `obtenerResumenPorDocumento(TipoDocumento, String)`, que devuelve un `ClienteResumenResponse` con solo el id y si el cliente está activo o no. `CuentaServiceImpl` ya no toca `Cliente`, `EstadoCliente` ni `IClienteRepository`, y las tres validaciones de abrir cuenta (404 si no existe, 403 si es otro cliente, 403 si no está habilitado) siguen en el mismo orden y con los mismos mensajes. Lo buscamos por documento y no por id porque así es como funciona la apertura hoy: el cliente manda su documento y nosotros lo comparamos con el id que viene en el token.

## Qué implica esto

**A favor:** las transferencias ya tienen dónde vivir sin ensuciar `cuenta`, y el patrón queda armado: cuando haga falta debitar, es agregar el método al lado de `acreditar` en `ICuentaService`. El saldo de una cuenta solo se modifica desde `CuentaServiceImpl`, así que las reglas de si se puede mover plata o no quedan en un solo sitio. Ningún archivo de `transaccion` importa la entidad ni el repositorio de `cuenta`, ningún archivo de `cuenta` importa nada de `transaccion`, y `cuenta` dejó de depender de las clases internas de `cliente`. En las pruebas también se nota: `DepositoServiceImplTest` ahora mockea `ICuentaService` y ya no tiene que armar entidades `Cuenta` ni meterles estados por reflexión.

**En contra:** `TipoDocumento` sigue viviendo en `cliente/entity` y lo siguen importando `AperturaCuentaRequest`, la firma de `IClienteService` y algunas pruebas de `cuenta`. Lo dejamos así a propósito y queda como deuda conocida: es un enum sin estado ni comportamiento, así que el acople es mucho menos grave que el de entidad y repositorio que sí corregimos acá, pero en algún momento habría que moverlo a un lugar que no sea `entity`. Por otro lado, ahora hay dos controllers en módulos distintos (`CuentaController` y `DepositoController`) que comparten el prefijo `/api/cuentas`, y eso hay que tenerlo presente para no repetir una ruta sin darse cuenta. Y `ICuentaService` va a ir creciendo con cada operación que mueva saldo, así que toca estar pendientes de que no se nos convierta en un servicio que hace de todo.
