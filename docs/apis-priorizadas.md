# APIs priorizadas

Endpoints sacados de los controllers de `main`. El rol sale de `@PreAuthorize` cuando el endpoint lo tiene; si no, sale de la validación que hace el servicio, y eso se indica en la columna de rol.

## auth-api

| endpoint | HU | sprint | rol | prioridad | razón |
| --- | --- | --- | --- | --- | --- |
| `POST /api/v1/auth/login` | HU-18 Login usuario | 1 | Público (`permitAll` en `SecurityConfig`) | alta | Sin token no se puede usar ningún otro endpoint. |
| `POST /api/v1/clientes` | HU-01 Registro de cliente | 1 | ADMIN (`@PreAuthorize`) | alta | Sin cliente registrado no hay cuentas ni operaciones. Crea el cliente y su usuario en una sola transacción. |
| `PATCH /api/v1/clientes/{clienteId}` | HU-03 Actualización de datos del cliente | 3 | ADMIN (`@PreAuthorize`) | media | No mueve dinero, pero cambiar el correo cambia el usuario con el que el cliente inicia sesión. Cada cambio queda en la auditoría. |
| `GET /api/v1/clientes` | HU-02 Consulta de información del cliente | 3 | CLIENTE (solo su propio documento), ADMIN y CAJERO (cualquiera) con `@PreAuthorize`. La regla de propiedad la decide `ConsultaClienteServiceImpl` (ADR-0005) | media | Es de solo lectura, pero muestra datos personales completos. Las cuentas salen sin saldos. |

## core-api

| endpoint | HU | sprint | rol | prioridad | razón |
| --- | --- | --- | --- | --- | --- |
| `POST /api/v1/cuentas` | HU-05 Apertura de cuenta | 1 | CLIENTE, sin `@PreAuthorize`: `CuentaServiceImpl.abrirCuenta` compara el cliente del documento con el `clienteId` del token, y ADMIN y CAJERO no traen `clienteId`, así que reciben 403 (o 404 si el documento no existe) | alta | Sin cuenta no hay depósitos, retiros, transferencias ni historial. |
| `POST /api/v1/cuentas/{cuentaId}/depositos` | HU-10 Depósito a cuenta | 1 | ADMIN, CAJERO (`@PreAuthorize`) | alta | Mueve dinero, y lo hacen ADMIN o CAJERO en nombre del cliente. |
| `POST /api/v1/cuentas/{cuentaId}/retiros` | HU-11 Retiro de cuenta | 2 | CLIENTE titular, sin `@PreAuthorize`: `validarTitularidad` responde 403 si la cuenta no es del `clienteId` del token o si el token no trae `clienteId` | alta | Mueve dinero. |
| `POST /api/v1/cuentas/{cuentaId}/transferencias` | HU-09a Transferencia exitosa, HU-09b Validación de montos y fondos, HU-09c Validación de cuenta destino | 2 | CLIENTE titular de la cuenta origen, sin `@PreAuthorize`: misma validación que el retiro | alta | Mueve dinero entre dos cuentas a la vez. Las tres HU están en el mismo endpoint. |
| `GET /api/v1/cuentas/{numeroCuenta}/saldo` | HU-06 Consulta de saldo | 1 | CLIENTE (solo sus cuentas), ADMIN y CAJERO (cualquier cuenta), sin `@PreAuthorize`: lo decide `SaldoServiceImpl` (ADR-0005) | media | Es de solo lectura, pero muestra el saldo disponible, que es lo que se puede retirar o transferir. |
| `POST /api/v1/cuentas/{cuentaId}/bloquear` | HU-07a Bloqueo de cuenta | 2 | ADMIN (`@PreAuthorize`) | media | No mueve dinero, pero una cuenta bloqueada no puede retirar ni transferir. |
| `POST /api/v1/cuentas/{cuentaId}/desbloquear` | HU-07a Bloqueo de cuenta | 2 | ADMIN (`@PreAuthorize`) | media | Es la única forma de devolver a ACTIVA una cuenta bloqueada. |
| `POST /api/v1/cuentas/{cuentaId}/cerrar` | HU-07b Cierre de cuenta | 2 | ADMIN (`@PreAuthorize`) | media | No mueve dinero (exige saldo $0) y no tiene vuelta atrás desde la API. |
| `GET /api/v1/clientes/{clienteId}/cuentas` | HU-08 Listado de cuentas por cliente | 3 | CLIENTE (solo las suyas), ADMIN y CAJERO (cualquiera) con `@PreAuthorize`; lo decide `ListadoCuentasServiceImpl` (ADR-0005) | baja | Es de solo lectura y, si falla, no afecta saldos ni bloquea otra HU. |

## reportes-api

| endpoint | HU | sprint | rol | prioridad | razón |
| --- | --- | --- | --- | --- | --- |
| `GET /api/v1/reportes/cuentas/{cuentaId}/movimientos` | HU-13 Historial de transacciones por cuenta | 2 | CLIENTE (solo sus cuentas), ADMIN y CAJERO (cualquier cuenta), sin `@PreAuthorize`: lo decide `MovimientoServiceImpl` (ADR-0005) | baja | Es de solo lectura y, si falla, no afecta saldos ni bloquea otra HU. |
| `GET /api/v1/reportes/transacciones/{transaccionId}` | HU-14 Detalle de una transacción | 3 | CLIENTE y ADMIN (`@PreAuthorize`, el CAJERO recibe 403). Al CLIENTE `TransaccionServiceImpl` le exige que la cuenta origen o la destino sea suya y le responde 403 también si la transacción no existe (ADR-0005) | baja | Es de solo lectura y, si falla, no afecta saldos ni bloquea otra HU. El usuario que originó la transacción solo se le muestra al ADMIN. |

Las tres APIs dejan además `/error`, `/swagger-ui/**`, `/swagger-ui.html` y `/v3/api-docs/**` sin token. No son de ninguna HU.
