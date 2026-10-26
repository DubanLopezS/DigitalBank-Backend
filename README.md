# BackendBancoDigital

## Endpoints

### Sprint 1

| Método y ruta | HU | Qué hace | Rol | Códigos de respuesta |
|---|---|---|---|---|
| `POST /api/auth/login` | HU-18 Login | Autentica un usuario y devuelve un JWT | Público | `200` login exitoso · `400` email/password vacíos · `401` credenciales inválidas · `429` cuenta bloqueada temporalmente (3 intentos fallidos) |
| `POST /api/clientes` | HU-01 Registro de cliente | Registra un cliente nuevo y crea su usuario en la misma transacción | ADMIN | `201` creado · `400` datos inválidos o menor de edad · `401` sin token · `403` no es ADMIN · `409` documento o email ya existe |
| `POST /api/cuentas` | HU-05 Apertura de cuenta | El cliente abre una cuenta de ahorros o corriente a su propio nombre | CLIENTE (titular; validado en el servicio) | `201` creada · `400` datos inválidos · `401` sin token · `403` el documento no es del cliente autenticado, o el cliente no está activo · `404` documento no encontrado |
| `POST /api/cuentas/{cuentaId}/depositos` | HU-10 Depósito a cuenta | Registra un depósito y actualiza el saldo | ADMIN, CAJERO | `201` creado · `400` monto inválido u origen faltante · `401` sin token · `403` no es ADMIN/CAJERO · `404` cuenta no encontrada · `409` cuenta no disponible (cerrada) |
| `GET /api/cuentas/{numeroCuenta}/saldo` | HU-06 Consulta de saldo | Devuelve el saldo contable y disponible de una cuenta | CLIENTE (solo la suya), ADMIN, CAJERO (cualquiera) | `200` ok · `401` sin token · `403` cuenta ajena (CLIENTE) · `404` cuenta no encontrada (solo si consulta ADMIN/CAJERO) |

### Sprint 2

| Método y ruta | HU | Qué hace | Rol | Códigos de respuesta |
|---|---|---|---|---|
| `POST /api/cuentas/{cuentaId}/bloquear` | HU-07a Bloqueo de cuenta | Bloquea una cuenta activa, con auditoría del motivo | ADMIN | `200` ok · `400` motivo inválido (menos de 10 o más de 255 caracteres) · `401` sin token · `403` no es ADMIN · `404` cuenta no encontrada · `409` la cuenta no está activa |
| `POST /api/cuentas/{cuentaId}/desbloquear` | HU-07a Bloqueo de cuenta | Devuelve a ACTIVA una cuenta bloqueada | ADMIN | `200` ok · `400` motivo inválido · `401` sin token · `403` no es ADMIN · `404` cuenta no encontrada · `409` la cuenta no está bloqueada |
| `POST /api/cuentas/{cuentaId}/cerrar` | HU-07b Cierre de cuenta | Cierra una cuenta de forma permanente (exige saldo $0) | ADMIN | `200` ok · `400` motivo inválido · `401` sin token · `403` no es ADMIN · `404` cuenta no encontrada · `409` ya está cerrada, o no tiene saldo $0 |
| `POST /api/cuentas/{cuentaId}/retiros` | HU-11 Retiro de cuenta | El cliente titular retira dinero de su cuenta | CLIENTE (titular; validado en el servicio) | `201` creado · `400` monto inválido · `401` sin token · `403` cuenta no es del cliente autenticado · `404` cuenta no encontrada · `409` cuenta no disponible, o saldo insuficiente |
| `POST /api/cuentas/{cuentaId}/transferencias` | HU-09a Transferencia exitosa · HU-09b Validación de montos y fondos · HU-09c Validación de cuenta destino | Transfiere dinero entre dos cuentas, generando dos movimientos ligados | CLIENTE (titular de la cuenta origen; validado en el servicio) | `201` creado · `400` monto inválido, o cuenta origen igual a la destino · `401` sin token · `403` cuenta origen no es del cliente autenticado · `404` cuenta destino no encontrada · `409` saldo insuficiente, cuenta origen bloqueada o cerrada, o cuenta destino no disponible |
| `GET /api/reportes/cuentas/{cuentaId}/movimientos` | HU-13 Historial de movimientos | Lista los movimientos de una cuenta, con filtros de fecha/tipo y paginación | CLIENTE (solo las suyas), ADMIN, CAJERO (cualquiera) | `200` ok (puede venir vacío) · `400` rango de fechas, tipo o paginación inválidos · `401` sin token · `403` cuenta ajena (CLIENTE) · `404` cuenta no encontrada (solo ADMIN/CAJERO) |