# language: es
# HU-04 - Inactivación de cliente (Azure 114, tarea 197)
# Endpoint: POST /api/v1/clientes/{clienteId}/inactivar (core-api, ADR-0015)
# Los errores siguen el formato del ADR-0013: status, errorCode, message, path, traceId.

Característica: Inactivación de cliente
  Como administrador
  Quiero inactivar un cliente
  Para impedir que realice nuevas operaciones sin eliminar su historial

  Antecedentes:
    Dado que existe un usuario con rol "ADMIN" autenticado
    Y que existe el cliente "Ana Gómez" en estado "ACTIVO"
    Y que "Ana Gómez" tiene un usuario con rol "CLIENTE" y un token vigente

  # ---------------------------------------------------------------------------
  # AC-1: solo el rol ADMIN puede inactivar un cliente
  # ---------------------------------------------------------------------------

  Esquema del escenario: Un rol distinto de ADMIN no puede inactivar un cliente
    Dado que el usuario autenticado tiene rol "<rol>"
    Cuando envía POST /api/v1/clientes/{clienteId}/inactivar para "Ana Gómez" con motivo "Solicitud del cliente por cambio de banco"
    Entonces la respuesta tiene estado 403
    Y el errorCode es "ACCESO_NO_AUTORIZADO"
    Y el cliente "Ana Gómez" sigue en estado "ACTIVO"

    Ejemplos:
      | rol     |
      | CLIENTE |
      | CAJERO  |

  Escenario: Sin token no se puede inactivar un cliente
    Cuando se envía POST /api/v1/clientes/{clienteId}/inactivar para "Ana Gómez" sin token
    Entonces la respuesta tiene estado 401
    Y el cliente "Ana Gómez" sigue en estado "ACTIVO"

  # ---------------------------------------------------------------------------
  # AC-2: solo se inactiva un cliente con saldo $0 en todas sus cuentas
  # ---------------------------------------------------------------------------

  Escenario: Un cliente con saldo en alguna cuenta no se puede inactivar
    Dado que "Ana Gómez" tiene la cuenta "1000000001" ACTIVA con saldo $0
    Y que "Ana Gómez" tiene la cuenta "1000000002" BLOQUEADA con saldo $15.000
    Cuando el ADMIN inactiva a "Ana Gómez" con motivo "Solicitud del cliente por cambio de banco"
    Entonces la respuesta tiene estado 409
    Y el errorCode es "CLIENTE_CON_SALDOS_PENDIENTES"
    Y el mensaje es "El cliente tiene saldos pendientes"
    Y el cliente "Ana Gómez" sigue en estado "ACTIVO"
    Y la cuenta "1000000001" sigue en estado "ACTIVA"
    Y no se registra ningún movimiento administrativo

  # ---------------------------------------------------------------------------
  # AC-3: el cliente pasa a INACTIVO y sus cuentas activas a BLOQUEADA
  # ---------------------------------------------------------------------------

  Escenario: Inactivación exitosa de un cliente con saldo $0
    Dado que "Ana Gómez" tiene la cuenta "1000000001" ACTIVA con saldo $0
    Y que "Ana Gómez" tiene la cuenta "1000000002" BLOQUEADA con saldo $0
    Y que "Ana Gómez" tiene la cuenta "1000000003" CERRADA con saldo $0
    Cuando el ADMIN inactiva a "Ana Gómez" con motivo "Solicitud del cliente por cambio de banco"
    Entonces la respuesta tiene estado 200
    Y el campo "estadoAnterior" es "ACTIVO"
    Y el campo "estadoNuevo" es "INACTIVO"
    Y el campo "cuentasBloqueadas" contiene solo "1000000001"
    Y el cliente "Ana Gómez" queda en estado "INACTIVO"
    Y la cuenta "1000000001" queda en estado "BLOQUEADA"
    Y la cuenta "1000000002" sigue en estado "BLOQUEADA"
    Y la cuenta "1000000003" sigue en estado "CERRADA"

  Escenario: Un cliente sin cuentas también se puede inactivar
    Dado que "Ana Gómez" no tiene cuentas
    Cuando el ADMIN inactiva a "Ana Gómez" con motivo "Cliente registrado por error en sucursal"
    Entonces la respuesta tiene estado 200
    Y el campo "cuentasBloqueadas" está vacío
    Y el cliente "Ana Gómez" queda en estado "INACTIVO"

  Escenario: No se puede inactivar un cliente que no existe
    Cuando el ADMIN inactiva un cliente con un id que no existe
    Entonces la respuesta tiene estado 404
    Y el errorCode es "CLIENTE_NO_ENCONTRADO"

  # ---------------------------------------------------------------------------
  # AC-4: un cliente ya INACTIVO no se vuelve a inactivar
  # ---------------------------------------------------------------------------

  Escenario: Un cliente ya inactivo responde 409
    Dado que "Ana Gómez" ya fue inactivado
    Cuando el ADMIN inactiva a "Ana Gómez" con motivo "Segundo intento de inactivación"
    Entonces la respuesta tiene estado 409
    Y el errorCode es "CLIENTE_YA_INACTIVO"
    Y no se registra un nuevo movimiento administrativo

  # ---------------------------------------------------------------------------
  # AC-5: un cliente INACTIVO no puede operar
  # El token de "Ana Gómez" se emitió antes de la inactivación y sigue vigente:
  # el rechazo debe salir del estado del cliente en la base, no del token.
  # ---------------------------------------------------------------------------

  Escenario: Un cliente inactivo no puede iniciar sesión
    Dado que "Ana Gómez" fue inactivado
    Cuando "Ana Gómez" envía POST /api/v1/auth/login con su correo y su clave correcta
    Entonces la respuesta tiene estado 403
    Y el errorCode es "CLIENTE_NO_HABILITADO"
    Y la respuesta no trae token

  Escenario: Con la clave incorrecta no se revela que el cliente está inactivo
    Dado que "Ana Gómez" fue inactivado
    Cuando "Ana Gómez" envía POST /api/v1/auth/login con su correo y una clave incorrecta
    Entonces la respuesta tiene estado 401
    Y el errorCode no es "CLIENTE_NO_HABILITADO"

  Escenario: Un cliente inactivo no puede abrir cuentas
    Dado que "Ana Gómez" fue inactivado
    Cuando "Ana Gómez" envía POST /api/v1/cuentas con su documento y tipo de cuenta "AHORROS"
    Entonces la respuesta tiene estado 409
    Y el errorCode es "CLIENTE_NO_HABILITADO"
    Y no se crea ninguna cuenta

  Escenario: La cuenta de un cliente inactivo no recibe depósitos
    Dado que "Ana Gómez" tenía la cuenta "1000000001" con saldo $0 y fue inactivado
    Y que existe un usuario con rol "CAJERO" autenticado
    Cuando el CAJERO deposita $50.000 en efectivo en la cuenta "1000000001"
    Entonces la respuesta tiene estado 409
    Y el errorCode es "CLIENTE_NO_HABILITADO"
    Y el saldo de la cuenta "1000000001" sigue en $0

  Escenario: La cuenta de un cliente inactivo no recibe transferencias
    Dado que "Ana Gómez" tenía la cuenta "1000000001" con saldo $0 y fue inactivado
    Y que el cliente "Bruno Ríos" está ACTIVO y tiene la cuenta "2000000001" con saldo $100.000
    Cuando "Bruno Ríos" transfiere $20.000 desde "2000000001" a la cuenta "1000000001"
    Entonces la respuesta tiene estado 409
    Y el errorCode es "CUENTA_DESTINO_NO_DISPONIBLE"
    Y el saldo de la cuenta "2000000001" sigue en $100.000
    Y el saldo de la cuenta "1000000001" sigue en $0

  Escenario: Un cliente inactivo no puede retirar
    Dado que "Ana Gómez" tenía la cuenta "1000000001" y fue inactivado
    Cuando "Ana Gómez" retira $10.000 de la cuenta "1000000001" con su token vigente
    Entonces la respuesta tiene estado 409
    Y el errorCode es "CLIENTE_NO_HABILITADO"

  Escenario: Un cliente inactivo no puede hacer transferencias salientes
    Dado que "Ana Gómez" tenía la cuenta "1000000001" y fue inactivado
    Y que el cliente "Bruno Ríos" está ACTIVO y tiene la cuenta "2000000001"
    Cuando "Ana Gómez" transfiere $10.000 desde "1000000001" a la cuenta "2000000001" con su token vigente
    Entonces la respuesta tiene estado 409
    Y el errorCode es "CLIENTE_NO_HABILITADO"
    Y el saldo de la cuenta "2000000001" no cambia

  # ---------------------------------------------------------------------------
  # AC-6: el historial del cliente sigue disponible
  # ---------------------------------------------------------------------------

  Escenario: El historial de un cliente inactivo sigue disponible
    Dado que "Ana Gómez" tuvo un depósito y un retiro en la cuenta "1000000001"
    Y que su saldo quedó en $0 y fue inactivado
    Cuando el ADMIN consulta GET /api/v1/reportes/cuentas/{cuentaId}/movimientos de la cuenta "1000000001"
    Entonces la respuesta tiene estado 200
    Y aparecen el depósito y el retiro

  Escenario: El saldo de la cuenta de un cliente inactivo se puede consultar
    Dado que "Ana Gómez" tenía la cuenta "1000000001" con saldo $0 y fue inactivado
    Cuando el ADMIN consulta GET /api/v1/cuentas/1000000001/saldo
    Entonces la respuesta tiene estado 200
    Y el saldo contable es $0

  # ---------------------------------------------------------------------------
  # AC-7: la inactivación queda registrada con fecha, hora, usuario y motivo
  # ---------------------------------------------------------------------------

  Escenario: La inactivación queda registrada en la auditoría
    Dado que "Ana Gómez" tiene la cuenta "1000000001" ACTIVA con saldo $0
    Cuando el ADMIN inactiva a "Ana Gómez" con motivo "Solicitud del cliente por cambio de banco"
    Entonces la respuesta trae "fechaHora", "usuarioResponsableId" y "motivo"
    Y "usuarioResponsableId" es el id del ADMIN que hizo la petición
    Y en movimiento_administrativo_cliente hay un registro de "Ana Gómez" de "ACTIVO" a "INACTIVO" con ese motivo, usuario, fecha y hora
    Y en movimiento_administrativo_cuenta hay un BLOQUEO de la cuenta "1000000001" con el mismo motivo

  Esquema del escenario: El motivo es obligatorio y tiene mínimo 10 caracteres
    Cuando el ADMIN inactiva a "Ana Gómez" con motivo "<motivo>"
    Entonces la respuesta tiene estado 400
    Y el errorCode es "DATOS_INVALIDOS"
    Y el cliente "Ana Gómez" sigue en estado "ACTIVO"

    Ejemplos:
      | motivo          | caso                                   |
      |                 | vacío                                  |
      | corto           | menos de 10 caracteres                 |
      |    corto        | menos de 10 sin contar los espacios    |
