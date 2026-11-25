# Despliegue local: Docker, Kubernetes y monitoreo

Además del despliegue en Render, el proyecto se puede levantar completo en una sola máquina:

- Docker Compose: PostgreSQL, las tres APIs, Prometheus y Grafana, con un solo comando.
- Kubernetes (el que trae Docker Desktop): las tres APIs con varias réplicas detrás de un Service que reparte las peticiones.
- Observabilidad: cada API expone sus métricas en `/actuator/prometheus` (Spring Boot Actuator + Micrometer), Prometheus las lee cada 15 segundos y Grafana las muestra en el tablero "Digital Bank - APIs".

Todo lo de aquí usa una base PostgreSQL local y claves de demo. No toca Supabase ni Render.

## Requisitos

- Docker Desktop, con al menos 6 GB de memoria asignados (Settings → Resources).
- Para Kubernetes: en Docker Desktop, Settings → Kubernetes → Enable Kubernetes, con el tipo de clúster kubeadm. Con kubeadm, Kubernetes usa las imágenes que se construyen en Docker Desktop sin subirlas a Docker Hub.

## 1. Levantar todo con Docker Compose

Desde la raíz del repo:

```
docker compose up -d --build
```

La primera vez tarda varios minutos, porque construye las tres imágenes (Maven compila y corre las pruebas dentro de cada una). Para ver cómo van:

```
docker compose ps
docker compose logs -f core-api
```

core-api arranca primero y crea las tablas con Flyway. auth-api y reportes-api pueden reiniciarse un par de veces mientras esas tablas no existen; Docker las vuelve a levantar solo.

Cuando las tres APIs estén arriba, se crean los usuarios internos de la demo (ADMIN y CAJERO):

```
docker compose exec postgres psql -U digitalbank -d digitalbank -f /scripts/usuarios-demo.sql
```

Usuarios de la demo:

| Rol | Correo | Clave |
|---|---|---|
| ADMIN | admin@digitalbank.local | Admin12345* |
| CAJERO | cajero@digitalbank.local | Cajero12345* |

Direcciones:

| Servicio | URL |
|---|---|
| auth-api | http://localhost:8081/swagger-ui.html |
| core-api | http://localhost:8082/swagger-ui.html |
| reportes-api | http://localhost:8083/swagger-ui.html |
| Métricas de una API | http://localhost:8082/actuator/prometheus |
| Prometheus | http://localhost:9090 (Status → Targets) |
| Grafana | http://localhost:3000 (admin / admin) |

## 2. Generar tráfico y ver las métricas

```
python3 monitoreo/generar_trafico.py
```

En Windows también sirve `powershell -ExecutionPolicy Bypass -File monitoreo\generar-trafico.ps1`. El script registra un cliente, le abre dos cuentas y repite depósitos, retiros, transferencias, consultas de saldo y del historial, más algunos errores a propósito (401, 400, 404 y 409).

En Grafana, el tablero "Digital Bank - APIs" (carpeta Digital Bank) ya viene cargado con:

- estado de cada API,
- peticiones por segundo,
- latencia promedio por API y por código de estado,
- errores 4xx y 5xx,
- peticiones por endpoint,
- depósitos, retiros, transferencias y reversos por minuto,
- uso de CPU del proceso y del sistema,
- memoria JVM,
- conexiones a la base (pool Hikari) e hilos de la JVM.

También se puede importar el tablero de la comunidad para Micrometer: Dashboards → New → Import → ID `4701` → fuente de datos Prometheus.

## 3. Kubernetes

Las imágenes `digitalbank/core-api:1.0`, `digitalbank/auth-api:1.0` y `digitalbank/reportes-api:1.0` las deja construidas el paso 1. Para no duplicar memoria, se apagan las APIs de Docker Compose y se dejan Prometheus y Grafana, que también leen las métricas de Kubernetes:

```
docker compose stop core-api auth-api reportes-api
kubectl apply -f k8s/
kubectl get pods -n digitalbank -w
```

Cuando los pods estén en `Running` y `1/1`, se crean los usuarios de la demo en la base de Kubernetes:

```
Get-Content local\usuarios-demo.sql | kubectl exec -i -n digitalbank deploy/postgres -- psql -U digitalbank -d digitalbank
```

Las APIs quedan en:

| API | URL |
|---|---|
| auth-api | http://localhost:30081 |
| core-api | http://localhost:30082 |
| reportes-api | http://localhost:30083 |

core-api arranca con 2 réplicas. Para mostrar el escalamiento horizontal y el balanceo:

```
kubectl scale deployment core-api -n digitalbank --replicas=3
kubectl get pods -n digitalbank -l app=core-api -o wide
powershell -ExecutionPolicy Bypass -File monitoreo\generar-trafico.ps1 -Auth http://localhost:30081 -Core http://localhost:30082 -Reportes http://localhost:30083
kubectl get endpoints core-api -n digitalbank
```

El último comando muestra la IP de cada réplica detrás del Service de core-api: el Service reparte las peticiones entre todas ellas. Si se borra un pod (`kubectl delete pod <nombre> -n digitalbank`), Kubernetes crea otro solo.

Para apagar todo:

```
kubectl delete -f k8s/
docker compose down
```

`docker compose down -v` también borra los datos de PostgreSQL, Prometheus y Grafana.

## Sin instalar nada: GitHub Codespaces

Si no se puede instalar Docker Desktop, todo lo anterior corre en un Codespace (un computador en la nube que se abre desde el navegador y ya trae Docker). En GitHub: botón verde Code → Codespaces → los tres puntos → New with options, elegir la rama y la máquina de 4 núcleos. Luego, en la terminal del Codespace:

```
docker compose up -d --build
docker compose ps
docker compose exec postgres psql -U digitalbank -d digitalbank -f /scripts/usuarios-demo.sql
python3 monitoreo/generar_trafico.py
```

La pestaña PORTS del Codespace abre Grafana (3000), Prometheus (9090) y las APIs en el navegador.

Para Kubernetes en el Codespace se usa kind (Kubernetes dentro de Docker):

```
curl -Lo kind https://kind.sigs.k8s.io/dl/v0.23.0/kind-linux-amd64 && chmod +x kind && sudo mv kind /usr/local/bin/kind
kubectl version --client || (curl -LO https://dl.k8s.io/release/v1.30.0/bin/linux/amd64/kubectl && chmod +x kubectl && sudo mv kubectl /usr/local/bin/kubectl)
docker compose stop core-api auth-api reportes-api
kind create cluster --name digitalbank
kind load docker-image digitalbank/core-api:1.0 digitalbank/auth-api:1.0 digitalbank/reportes-api:1.0 --name digitalbank
kubectl apply -f k8s/
kubectl get pods -n digitalbank -w
```

kind no publica los NodePort en la máquina, así que las APIs se exponen con port-forward (cada uno en segundo plano):

```
kubectl port-forward -n digitalbank svc/core-api 30082:8082 --address 0.0.0.0 > /dev/null 2>&1 &
kubectl port-forward -n digitalbank svc/auth-api 30081:8081 --address 0.0.0.0 > /dev/null 2>&1 &
kubectl port-forward -n digitalbank svc/reportes-api 30083:8083 --address 0.0.0.0 > /dev/null 2>&1 &
kubectl exec -i -n digitalbank deploy/postgres -- psql -U digitalbank -d digitalbank < local/usuarios-demo.sql
python3 monitoreo/generar_trafico.py --auth http://localhost:30081 --core http://localhost:30082 --reportes http://localhost:30083
```

port-forward se conecta a un solo pod. El balanceo entre réplicas se muestra con el escalamiento y las IP de cada réplica detrás del Service:

```
kubectl scale deployment core-api -n digitalbank --replicas=3
kubectl get pods -n digitalbank -l app=core-api -o wide
kubectl get endpoints core-api -n digitalbank
```

## Por qué está así

- `/actuator/health` y `/actuator/prometheus` no piden token. En Render solo se expone `health`; `prometheus` se activa con la variable `METRICS_EXPOSURE`, que solo ponen docker-compose y Kubernetes.
- La URL de la base de Render lleva `sslmode=require`. En local se reemplaza con `SPRING_DATASOURCE_URL`, porque el PostgreSQL del contenedor no usa SSL.
- Los Dockerfile son los mismos que usa Render: no hay una imagen distinta para local.
- Kubernetes revisa `/actuator/health/liveness` y `/actuator/health/readiness` para saber si reiniciar un pod o si mandarle tráfico.
