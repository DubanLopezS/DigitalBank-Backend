# ADR-0014: Monto de apertura como saldo inicial y mensaje de bloqueo en el login

**Fecha:** noviembre de 2026
**Responsable:** Equipo Digital Bank Backend
**Estado:** Aceptado

**Nota:** este ADR se escribió el 4 de noviembre de 2026, para cerrar el hallazgo HZ-11 de Calidad. El equipo decidió documentar las dos reglas tal como están implementadas.

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

- **Que los correos inexistentes también acumulen intentos y respondan el mismo 429**: cierra el hueco, pero requiere guardar intentos de correos que no existen (tabla nueva y migración V6). También permitiría que alguien llene esa tabla con correos inventados.
- **Responder siempre el 401 genérico, incluso a un usuario bloqueado**: cierra el hueco con poco código, pero el usuario real bloqueado no sabe por qué no puede entrar y se incumple el mensaje que la HU-18 pide.
- **Mantener el comportamiento (decisión tomada)**: cumple la HU tal como está escrita.

**Qué decidimos.** Se mantiene el mensaje específico de bloqueo temporal, porque la HU-18 lo pide de forma explícita. Se acepta como riesgo conocido que alguien pueda distinguir correos existentes por ese mensaje.

**Qué implica**

- A favor: el usuario real sabe que está bloqueado y por qué. No hay migración ni código nuevo.
- En contra: se puede enumerar correos del banco. Lo reducen dos cosas: el bloqueo dura 1 hora y el atacante necesita al menos tres intentos fallidos por correo. Si se quiere cerrar el hueco más adelante, el camino es la primera alternativa, con la migración V6.