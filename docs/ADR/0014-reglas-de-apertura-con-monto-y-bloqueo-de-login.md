# ADR-0014: Monto de apertura como saldo inicial y mensaje de bloqueo en el login

**Fecha:** noviembre de 2026
**Responsable:** Equipo Digital Bank Backend
**Estado:** Aceptado

**Nota:** este ADR se escribió el 4 de noviembre de 2026, para cerrar el hallazgo HZ-11 de Calidad. El equipo decidió documentar las dos reglas tal como están implementadas.

**Actualización:** el 27 de noviembre de 2026 se agregó a la Decisión 2 el riesgo de que un tercero bloquee el login de otra persona.

## Contexto

Calidad reportó dos reglas sin definir en las historias de usuario de apertura de cuenta (HU-05) y de login (HU-18):

1. La apertura con monto no genera transacción.
2. La respuesta 429 de un login bloqueado confirma que el correo existe.

## Decisión 1: el monto de apertura es un saldo inicial y no genera transacción

**Alternativas que consideramos**

- **Registrar un DEPOSITO**: la tabla `transaccion` exige un `origen` (EFECTIVO, CHEQUE u OTRO) en los depósitos, y en la apertura no hay un origen real. Elegir uno sería guardar un dato inventado.
- **Crear un tipo nuevo (por ejemplo APERTURA)**: obliga a una migración que amplíe el CHECK del tipo de transacción y a cambiar el historial y los reportes. Es más trabajo del que justifica el hallazgo.
- **No registrar nada (decisión tomada)**: es lo que ya hace el sistema.

**Qué decidimos.** El monto de apertura (opcional, mayor o igual a 0 según la HU) se guarda como `saldo_contable` inicial de la cuenta y no crea ninguna fila en `transaccion`.

**Qué implica**

- A favor: no se inventa un origen y no hay migración.
- En contra: ese dinero no aparece en el historial de movimientos (HU-13). El saldo de una cuenta no se puede reconstruir solo sumando sus transacciones, hay que partir del saldo inicial. El `saldo_anterior` del primer movimiento de una cuenta abierta con monto ya trae ese saldo.

## Decisión 2: el mensaje de bloqueo se mantiene y se acepta el riesgo de enumeración

**El problema.** La HU-18 pide dos cosas que chocan. Para un correo inexistente o una clave incorrecta, responde siempre "Credenciales inválidas" (401) para no revelar cuál falló. Pero al tercer intento fallido bloquea el acceso, y los intentos siguientes responden el mensaje específico de bloqueo (429). El contador vive en la fila de `usuario`, así que solo un correo que existe puede llegar a ese mensaje. Quien pruebe varios intentos con un correo ve 401 y luego 429 si existe, o 401 siempre si no existe.

**Alternativas que consideramos**

- **Que los correos inexistentes también acumulen intentos y respondan el mismo 429**: cierra el hueco, pero requiere guardar intentos de correos que no existen (tabla nueva y una migración nueva). También permitiría que alguien llene esa tabla con correos inventados.
- **Responder siempre el 401 genérico, incluso a un usuario bloqueado**: cierra el hueco con poco código, pero el usuario real bloqueado no sabe por qué no puede entrar y se incumple el mensaje que la HU-18 pide.
- **Mantener el comportamiento (decisión tomada)**: cumple la HU tal como está escrita.

**Qué decidimos.** Se mantiene el mensaje específico de bloqueo temporal, porque la HU-18 lo pide de forma explícita. Se acepta como riesgo conocido que alguien pueda distinguir correos existentes por ese mensaje.

**Qué implica**

- A favor: el usuario real sabe que está bloqueado y por qué. No hay migración ni código nuevo.
- En contra: se puede enumerar correos del banco. Lo reducen dos cosas: el bloqueo dura 1 hora y el atacante necesita al menos tres intentos fallidos por correo. Si se quiere cerrar el hueco más adelante, el camino es la primera alternativa, con una migración nueva.
- En contra: el contador de intentos está en la columna `intentos_fallidos` de la fila de `usuario` y no depende de quién intenta. `AuthServiceImpl` lo sube con cada clave incorrecta, venga de donde venga, y al tercer fallo `Usuario` pasa a BLOQUEADO_TEMPORAL por una hora. Así que cualquiera que conozca el correo de otra persona puede dejarle el login bloqueado por una hora con tres intentos fallidos, y repetirlo cada hora. Es una denegación de servicio contra un usuario: mientras dura el bloqueo, el dueño tampoco entra aunque ponga la clave correcta, porque el bloqueo se revisa antes que la clave. El contador solo vuelve a cero con un login exitoso o cuando vence el bloqueo, así que los tres fallos no tienen que ser seguidos. Los intentos durante el bloqueo no lo alargan. Hoy nada en el código lo impide; solo queda rastro en los logs con los eventos `LOGIN_FALLIDO`, que trae el `usuarioId` y el número de intentos, `USUARIO_BLOQUEADO_TEMPORAL` y `LOGIN_RECHAZADO_USUARIO_BLOQUEADO`. Con eso se puede ver que a un usuario lo están bloqueando, pero no desde dónde, porque los eventos no guardan la IP. Si se quiere cerrar, el camino es contar intentos por IP o por dispositivo además de por usuario, o pedir un captcha después de los fallos.