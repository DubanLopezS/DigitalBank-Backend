"""Genera tráfico contra las APIs para que Prometheus y Grafana muestren datos en la demo.

Hace un flujo real (registro de cliente, apertura de cuentas, depósitos, retiros,
transferencias, consultas) y algunos errores a propósito (401, 400, 404, 409).
Solo usa la librería estándar de Python, así que corre en Windows, Linux o Codespaces.

Uso, desde la raíz del repo:
    docker-compose:  python3 monitoreo/generar_trafico.py
    Kubernetes:      python3 monitoreo/generar_trafico.py --auth http://localhost:30081 --core http://localhost:30082 --reportes http://localhost:30083

Necesita los usuarios de local/usuarios-demo.sql.
"""
import argparse
import json
import random
import time
import urllib.error
import urllib.request


def llamar(metodo, url, cuerpo=None, token=None):
    datos = json.dumps(cuerpo).encode("utf-8") if cuerpo is not None else None
    peticion = urllib.request.Request(url, data=datos, method=metodo)
    peticion.add_header("Content-Type", "application/json")
    if token:
        peticion.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(peticion, timeout=30) as respuesta:
            texto = respuesta.read().decode("utf-8")
            return respuesta.status, (json.loads(texto) if texto else None)
    except urllib.error.HTTPError as error:
        return error.code, None
    except urllib.error.URLError:
        return 0, None


def exigir(resultado, paso):
    estado, datos = resultado
    if not 200 <= estado < 300:
        raise SystemExit(f"Falló '{paso}' con estado {estado}. "
                         "¿Están arriba las APIs y se corrió local/usuarios-demo.sql?")
    return datos


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--auth", default="http://localhost:8081")
    p.add_argument("--core", default="http://localhost:8082")
    p.add_argument("--reportes", default="http://localhost:8083")
    p.add_argument("--iteraciones", type=int, default=30)
    p.add_argument("--admin-email", default="admin@digitalbank.local")
    p.add_argument("--admin-clave", default="Admin12345*")
    a = p.parse_args()

    print("Login del ADMIN...")
    token_admin = exigir(llamar("POST", f"{a.auth}/api/v1/auth/login",
                                {"email": a.admin_email, "password": a.admin_clave}), "login ADMIN")["token"]

    sufijo = random.randint(100000, 999999)
    documento = f"10{sufijo}"
    correo = f"cliente{sufijo}@demo.local"
    clave = "Cliente12345*"

    print(f"Registrando cliente {correo} (HU-01)...")
    exigir(llamar("POST", f"{a.auth}/api/v1/clientes", {
        "tipoDocumento": "CC", "numeroDocumento": documento, "nombres": "Cliente",
        "apellidos": f"Demo {sufijo}", "fechaNacimiento": "1998-05-10",
        "telefono": f"300{sufijo}0", "email": correo, "password": clave,
    }, token_admin), "registro de cliente")

    token_cliente = exigir(llamar("POST", f"{a.auth}/api/v1/auth/login",
                                  {"email": correo, "password": clave}), "login cliente")["token"]

    print("Abriendo dos cuentas (HU-05)...")
    cuenta_a = exigir(llamar("POST", f"{a.core}/api/v1/cuentas", {
        "tipoDocumento": "CC", "numeroDocumento": documento, "tipoCuenta": "AHORROS", "montoApertura": 0,
    }, token_cliente), "apertura cuenta A")
    cuenta_b = exigir(llamar("POST", f"{a.core}/api/v1/cuentas", {
        "tipoDocumento": "CC", "numeroDocumento": documento, "tipoCuenta": "CORRIENTE", "montoApertura": 0,
    }, token_cliente), "apertura cuenta B")

    print(f"Generando tráfico: {a.iteraciones} vueltas (Ctrl+C para parar)...")
    for i in range(1, a.iteraciones + 1):
        estados = [
            # Exitosas
            llamar("POST", f"{a.core}/api/v1/cuentas/{cuenta_a['id']}/depositos",
                   {"monto": 50000, "origen": "EFECTIVO"}, token_admin)[0],
            llamar("POST", f"{a.core}/api/v1/cuentas/{cuenta_a['id']}/retiros",
                   {"monto": 5000}, token_cliente)[0],
            llamar("POST", f"{a.core}/api/v1/cuentas/{cuenta_a['id']}/transferencias",
                   {"cuentaDestino": cuenta_b["numeroCuenta"], "monto": 10000, "descripcion": f"Demo {i}"},
                   token_cliente)[0],
            llamar("GET", f"{a.core}/api/v1/cuentas/{cuenta_a['numeroCuenta']}/saldo", token=token_cliente)[0],
            llamar("GET", f"{a.reportes}/api/v1/reportes/cuentas/{cuenta_a['id']}/movimientos",
                   token=token_cliente)[0],
            # Errores a propósito
            llamar("GET", f"{a.core}/api/v1/cuentas/{cuenta_a['numeroCuenta']}/saldo")[0],  # 401 sin token
            llamar("POST", f"{a.core}/api/v1/cuentas/{cuenta_a['id']}/retiros",
                   {"monto": -1}, token_cliente)[0],  # 400 monto inválido
            llamar("POST", f"{a.core}/api/v1/cuentas/{cuenta_a['id']}/transferencias",
                   {"cuentaDestino": "9999999999", "monto": 1000}, token_cliente)[0],  # 404 destino
            llamar("POST", f"{a.core}/api/v1/cuentas/{cuenta_b['id']}/retiros",
                   {"monto": 999999999}, token_cliente)[0],  # 409 saldo insuficiente
        ]
        print(f"Vuelta {i:3}: {' '.join(str(e) for e in estados)}")
        time.sleep(1)

    print("Listo. Revisa Grafana (tablero 'Digital Bank - APIs').")


if __name__ == "__main__":
    main()
