# Genera tráfico contra las APIs para que Prometheus y Grafana muestren datos en la demo.
# Hace un flujo real (registro de cliente, apertura de cuenta, depósitos, retiros,
# transferencias, consultas) y algunos errores a propósito (401, 400, 404, 409).
#
# Uso, desde la raíz del repo:
#   docker-compose:  powershell -ExecutionPolicy Bypass -File monitoreo\generar-trafico.ps1
#   Kubernetes:      powershell -ExecutionPolicy Bypass -File monitoreo\generar-trafico.ps1 -Auth http://localhost:30081 -Core http://localhost:30082 -Reportes http://localhost:30083
#
# Necesita los usuarios de local/usuarios-demo.sql.

param(
    [string]$Auth = "http://localhost:8081",
    [string]$Core = "http://localhost:8082",
    [string]$Reportes = "http://localhost:8083",
    [int]$Iteraciones = 30,
    [string]$AdminEmail = "admin@digitalbank.local",
    [string]$AdminClave = "Admin12345*"
)

$ErrorActionPreference = "Stop"

function Llamar {
    param([string]$Metodo, [string]$Url, $Cuerpo = $null, [string]$Token = $null)
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $parametros = @{ Method = $Metodo; Uri = $Url; Headers = $headers; ContentType = "application/json"; UseBasicParsing = $true }
    if ($null -ne $Cuerpo) { $parametros["Body"] = [System.Text.Encoding]::UTF8.GetBytes(($Cuerpo | ConvertTo-Json -Depth 5)) }
    try {
        $r = Invoke-WebRequest @parametros
        $datos = $null
        if ($r.Content) { $datos = $r.Content | ConvertFrom-Json }
        return @{ Estado = [int]$r.StatusCode; Datos = $datos }
    } catch {
        $estado = 0
        if ($_.Exception.Response) { $estado = [int]$_.Exception.Response.StatusCode }
        return @{ Estado = $estado; Datos = $null }
    }
}

function Exigir($respuesta, [string]$paso) {
    if ($respuesta.Estado -lt 200 -or $respuesta.Estado -ge 300) {
        throw "Falló '$paso' con estado $($respuesta.Estado). ¿Están arriba las APIs y se corrió local/usuarios-demo.sql?"
    }
    return $respuesta.Datos
}

Write-Host "Login del ADMIN..."
$admin = Exigir (Llamar POST "$Auth/api/v1/auth/login" @{ email = $AdminEmail; password = $AdminClave }) "login ADMIN"
$tokenAdmin = $admin.token

# Cliente nuevo en cada corrida, para no chocar con documentos o correos ya usados.
$sufijo = Get-Random -Minimum 100000 -Maximum 999999
$documento = "10$sufijo"
$correo = "cliente$sufijo@demo.local"
$clave = "Cliente12345*"

Write-Host "Registrando cliente $correo (HU-01)..."
$null = Exigir (Llamar POST "$Auth/api/v1/clientes" @{
    tipoDocumento = "CC"; numeroDocumento = $documento; nombres = "Cliente"; apellidos = "Demo $sufijo"
    fechaNacimiento = "1998-05-10"; telefono = "300$($sufijo)0"; email = $correo; password = $clave
} $tokenAdmin) "registro de cliente"

$tokenCliente = (Exigir (Llamar POST "$Auth/api/v1/auth/login" @{ email = $correo; password = $clave }) "login cliente").token

Write-Host "Abriendo dos cuentas (HU-05)..."
$cuentaA = Exigir (Llamar POST "$Core/api/v1/cuentas" @{ tipoDocumento = "CC"; numeroDocumento = $documento; tipoCuenta = "AHORROS"; montoApertura = 0 } $tokenCliente) "apertura cuenta A"
$cuentaB = Exigir (Llamar POST "$Core/api/v1/cuentas" @{ tipoDocumento = "CC"; numeroDocumento = $documento; tipoCuenta = "CORRIENTE"; montoApertura = 0 } $tokenCliente) "apertura cuenta B"

Write-Host "Generando tráfico: $Iteraciones vueltas (Ctrl+C para parar)..."
for ($i = 1; $i -le $Iteraciones; $i++) {
    $estados = @()
    # Exitosas
    $estados += (Llamar POST "$Core/api/v1/cuentas/$($cuentaA.id)/depositos" @{ monto = 50000; origen = "EFECTIVO" } $tokenAdmin).Estado
    $estados += (Llamar POST "$Core/api/v1/cuentas/$($cuentaA.id)/retiros" @{ monto = 5000 } $tokenCliente).Estado
    $estados += (Llamar POST "$Core/api/v1/cuentas/$($cuentaA.id)/transferencias" @{ cuentaDestino = $cuentaB.numeroCuenta; monto = 10000; descripcion = "Demo $i" } $tokenCliente).Estado
    $estados += (Llamar GET "$Core/api/v1/cuentas/$($cuentaA.numeroCuenta)/saldo" $null $tokenCliente).Estado
    $estados += (Llamar GET "$Reportes/api/v1/reportes/cuentas/$($cuentaA.id)/movimientos" $null $tokenCliente).Estado
    # Errores a propósito
    $estados += (Llamar GET "$Core/api/v1/cuentas/$($cuentaA.numeroCuenta)/saldo").Estado                                  # 401 sin token
    $estados += (Llamar POST "$Core/api/v1/cuentas/$($cuentaA.id)/retiros" @{ monto = -1 } $tokenCliente).Estado            # 400 monto inválido
    $estados += (Llamar POST "$Core/api/v1/cuentas/$($cuentaA.id)/transferencias" @{ cuentaDestino = "9999999999"; monto = 1000 } $tokenCliente).Estado  # 404 destino
    $estados += (Llamar POST "$Core/api/v1/cuentas/$($cuentaB.id)/retiros" @{ monto = 999999999 } $tokenCliente).Estado     # 409 saldo insuficiente
    Write-Host ("Vuelta {0,3}: {1}" -f $i, ($estados -join " "))
    Start-Sleep -Seconds 1
}

Write-Host "Listo. Revisa Grafana en http://localhost:3000 (tablero 'Digital Bank - APIs')."
