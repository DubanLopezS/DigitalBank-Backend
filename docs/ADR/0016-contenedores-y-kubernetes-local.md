# ADR-0016: Imágenes Docker, Docker Compose y Kubernetes local para las tres APIs

**Fecha:** noviembre de 2026
**Responsable:** Equipo Digital Bank Backend
**Estado:** Aceptado

**Nota:** este ADR se escribió el 27 de noviembre de 2026, con la decisión ya tomada. Los Dockerfile de `auth-api` y `core-api` existen desde el 9 de octubre de 2026, el de `reportes-api` desde el 17 de octubre, y el `docker-compose.yml`, la carpeta `k8s` y el monitoreo se agregaron el 22 de noviembre de 2026.

## Contexto

Desde el ADR-0009 el backend son tres APIs, `auth-api`, `core-api` y `reportes-api`, que comparten una base PostgreSQL. En producción corren en Render free contra Supabase (ADR-0012). Para el Sprint 3 necesitábamos poder levantar todo el sistema en una sola máquina, sin tocar Supabase ni Render, para la demo, para mostrar varias réplicas de una API detrás de un balanceador y para ver métricas de las tres APIs en Prometheus y Grafana.

Ya teníamos un Dockerfile por API, que es lo que usa Render para construir cada servicio. Faltaba decidir cómo levantar la base, las tres APIs y el monitoreo juntos, y cómo orquestar las réplicas.

## Alternativas que consideramos

- **Solo Docker Compose, sin Kubernetes**: levanta todo con un comando y ya lo teníamos para el día a día. Lo descartamos como única opción porque en nuestro Compose cada API tiene `container_name` y un puerto fijo en la máquina, así que no se puede levantar más de una réplica de la misma API, y si alguien borra un contenedor, nadie lo vuelve a crear. Dato del equipo: los lineamientos del curso para el Sprint 3 piden "contenedores y orquestación cuando aplique", y el Compose solo cubre la parte de contenedores. Con Kubernetes se puede mostrar lo que permite la orquestación: `03-core-api.yml` arranca `core-api` con 2 réplicas detrás de un Service NodePort que reparte las peticiones entre ellas, y `docs/despliegue-local.md` muestra cómo escalarla a 3 con `kubectl scale`, ver con `kubectl get endpoints` las IP de cada réplica y borrar un pod para que Kubernetes cree otro.
- **Minikube o kind en vez del Kubernetes de Docker Desktop**: los dos crean un clúster aparte, y a ese clúster hay que pasarle las imágenes que construye el Compose. En la guía se ve con kind, que necesita `kind load docker-image` para cada imagen y no publica los NodePort en la máquina, así que hay que usar `kubectl port-forward`. Con el Kubernetes de Docker Desktop, en modo kubeadm, el clúster usa directamente las imágenes construidas en Docker Desktop y los NodePort quedan en `localhost`. Además, `docs/despliegue-local.md` ya pide Docker Desktop para el Compose, y para Kubernetes solo hay que activarlo en ese mismo Docker Desktop, en Settings, Kubernetes, Enable Kubernetes, así que no hay que instalar otra herramienta. kind no quedó descartado del todo: es lo que se usa en GitHub Codespaces, donde no hay Docker Desktop.
- **Helm o kube-prometheus-stack en vez de manifiestos propios**: Helm sirve para parametrizar despliegues en varios entornos, y aquí solo hay uno, el local, con seis archivos YAML cortos. kube-prometheus-stack instala Prometheus, Grafana y su operador dentro del clúster. Lo descartamos porque Prometheus y Grafana ya corren en el Compose y desde ahí leen también las APIs de Kubernetes, y porque Docker Desktop ya necesita al menos 6 GB de memoria para todo esto, según la guía. Meter otro Prometheus y otro Grafana dentro del clúster sería duplicar lo que ya está.
- **Desplegar Kubernetes en la nube**: sería un entorno parecido a uno real, pero cuesta dinero y la producción ya está en Render free y Supabase (ADR-0012). Dato del equipo: el proyecto es de curso y no tiene presupuesto para un clúster administrado.

## Qué decidimos

**Imágenes.** Cada API tiene su `Dockerfile` en su carpeta. Los tres son multi-stage: la primera etapa usa `eclipse-temurin:21-jdk` y compila con `./mvnw -pl <api> -am clean package -B`, y la segunda usa `eclipse-temurin:21-jre` y solo copia el `.jar`. El contexto de build es la raíz del repo, porque cada API depende del módulo `common`. La imagen arranca con `java -Xmx400m -jar app.jar` y expone el puerto de su API: 8081 para `auth-api`, 8082 para `core-api` y 8083 para `reportes-api`. `.dockerignore` deja por fuera `target`, `.git`, `.env`, `docs` y los `.md`. Según `docs/despliegue-local.md`, son los mismos Dockerfile que usa Render: no hay una imagen distinta para local.

**Docker Compose.** `docker-compose.yml` levanta PostgreSQL 16, las tres APIs, Prometheus `v2.53.0` y Grafana `11.1.0`. Las tres APIs reciben las mismas variables: `SPRING_DATASOURCE_URL`, que apunta al contenedor `postgres` sin SSL, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` y `METRICS_EXPOSURE`. PostgreSQL publica el puerto 5433 en la máquina y guarda los datos en un volumen.

El orden de arranque está así en el archivo:

- `postgres` tiene un `healthcheck` con `pg_isready` cada 5 segundos y hasta 10 reintentos.
- `core-api` espera a `postgres` con `condition: service_healthy`. Es la dueña del esquema: es la única con `spring.flyway.enabled=true`, así que es la que corre las migraciones al arrancar.
- `auth-api` y `reportes-api` tienen `depends_on: core-api` sin condición, así que solo esperan a que el contenedor de `core-api` haya arrancado, no a que Flyway termine.
- `auth-api` tiene `ddl-auto=validate`. Si arranca antes de que existan las tablas, falla, y como tiene `restart: on-failure`, Docker la vuelve a levantar hasta que las tablas existan.
- `reportes-api` usa JdbcClient sin JPA (ADR-0011), así que no valida tablas al arrancar. Arranca aunque las tablas no existan y sus consultas fallan hasta que Flyway termine. También tiene `restart: on-failure`.
- Prometheus espera a que arranquen las tres APIs y Grafana espera a Prometheus, también sin condición.

**Kubernetes.** Usamos el Kubernetes que trae Docker Desktop, con manifiestos YAML propios en `k8s/` y sin Helm. Se aplican todos con `kubectl apply -f k8s/`:

- `00-namespace.yml`: el namespace `digitalbank`.
- `01-configuracion.yml`: un Secret `digitalbank-secretos` con `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_USERNAME`, `DB_PASSWORD` y `JWT_SECRET`, y un ConfigMap `digitalbank-config` con `SPRING_DATASOURCE_URL` y `METRICS_EXPOSURE`.
- `02-postgres.yml`: un PersistentVolumeClaim `postgres-datos` de 1Gi en modo `ReadWriteOnce`, un Deployment de PostgreSQL 16 con 1 réplica, ese volumen montado en `/var/lib/postgresql/data` y una readinessProbe con `pg_isready` cada 5 segundos, y un Service `postgres` en el puerto 5432.
- `03-core-api.yml`, `04-auth-api.yml` y `05-reportes-api.yml`: un Deployment y un Service tipo NodePort por API.

| API | Réplicas | Puerto del contenedor | NodePort |
|---|---|---|---|
| `auth-api` | 1 | 8081 | 30081 |
| `core-api` | 2 | 8082 | 30082 |
| `reportes-api` | 1 | 8083 | 30083 |

Los tres Deployment de las APIs tienen los mismos recursos y las mismas probes:

- Recursos: piden 384Mi de memoria y 250m de CPU, con límite de 768Mi de memoria y 1000m de CPU.
- startupProbe a `/actuator/health/liveness` cada 10 segundos con `failureThreshold: 30`, que según el comentario del archivo le da a Spring hasta 5 minutos para arrancar.
- livenessProbe a `/actuator/health/liveness` cada 10 segundos.
- readinessProbe a `/actuator/health/readiness` cada 5 segundos.

Las probes funcionan porque los tres `application.properties` tienen `management.endpoint.health.probes.enabled=true`. Las imágenes son las que deja construidas el Compose, `digitalbank/<api>:1.0`, con `imagePullPolicy: IfNotPresent`. El orden de arranque se resuelve igual que en el Compose, con reinicios: el comentario de `04-auth-api.yml` dice que si `auth-api` arranca antes de que `core-api` termine las migraciones, Kubernetes la reinicia hasta que las tablas existan. Sobre las 2 réplicas de `core-api`, el comentario de `03-core-api.yml` dice que Flyway usa un bloqueo en la base, así que dos pods no migran a la vez.

**Monitoreo.** Prometheus corre en el Compose y lee `/actuator/prometheus` cada 15 segundos. En `monitoreo/prometheus.yml` hay seis jobs: `core-api`, `auth-api` y `reportes-api` apuntan a los servicios del Compose, y `core-api-k8s`, `auth-api-k8s` y `reportes-api-k8s` apuntan a `host.docker.internal:30082`, `30081` y `30083`, que son los NodePort de Kubernetes. El Compose le agrega a Prometheus `extra_hosts: host.docker.internal:host-gateway` para que ese nombre funcione. Si Kubernetes no está corriendo, los tres jobs de Kubernetes aparecen DOWN. Para no duplicar memoria, la guía apaga las APIs del Compose antes de aplicar `k8s/` y deja Prometheus y Grafana arriba.

**Sin Docker Desktop.** Quien no pueda instalar Docker Desktop puede correr el Compose en GitHub Codespaces, que ya trae Docker, siguiendo la sección "Sin instalar nada: GitHub Codespaces" de `docs/despliegue-local.md`. Ahí Kubernetes se reemplaza por kind, cargando las imágenes con `kind load docker-image` y exponiendo las APIs con `kubectl port-forward`.

**Producción no cambia.** Producción sigue en Render free y Supabase, sin Docker Compose ni Kubernetes (ADR-0012). Render construye cada servicio con su Dockerfile, pero no usa nada del Compose ni de `k8s/`. Los tres `application.properties` tienen `management.endpoints.web.exposure.include=${METRICS_EXPOSURE:health}`: en Render esa variable no existe, así que solo se expone `/actuator/health`, y `/actuator/prometheus` solo se activa en el Compose y en Kubernetes, que son los que ponen `METRICS_EXPOSURE`.

## Qué implica esto

**A favor:** cualquiera del equipo levanta el sistema completo con `docker compose up -d --build`, sin tocar Supabase ni Render. Como los Dockerfile son los mismos de Render, lo que se prueba en local corre con la misma imagen que producción. En Kubernetes se puede mostrar el escalamiento con `kubectl scale`, el reparto de peticiones del Service entre réplicas y la recreación de pods. Un solo Prometheus y un solo Grafana sirven para el Compose y para Kubernetes. Los manifiestos son cortos y se leen sin conocer Helm.

**En contra:**

- Los contenedores de las APIs corren como root. Los Dockerfile no tienen `USER`, así que el proceso usa el usuario por defecto de la imagen `eclipse-temurin:21-jre`, que es root.
- Los Dockerfile no tienen `HEALTHCHECK`. En Kubernetes no importa porque están las probes, pero en el Compose las APIs no tienen healthcheck, así que nadie puede esperar a que una API esté lista: `auth-api`, `reportes-api` y Prometheus solo esperan a que el contenedor haya arrancado.
- El orden de arranque depende de reinicios. `auth-api` falla y se reinicia hasta que `core-api` termina Flyway, en el Compose y en Kubernetes, y eso deja errores en los logs del arranque. `reportes-api` no se reinicia, pero sus consultas fallan si llegan antes de que existan las tablas.
- Las claves del Secret de `k8s/01-configuracion.yml` y las del Compose son de demo local y están escritas en el repo: `POSTGRES_PASSWORD`, `DB_PASSWORD`, `JWT_SECRET` y, en el Compose, `GF_SECURITY_ADMIN_PASSWORD` de Grafana. El Secret usa `stringData`, así que los valores quedan legibles en el archivo. Los dos archivos dicen que no son las de Render ni las de Supabase, y no se pueden reutilizar en ningún entorno real.
- Kubernetes local no es el entorno de producción. Las réplicas, el balanceo, las probes y los límites de recursos solo existen en la máquina de quien hace la demo, y ninguno de los archivos de `k8s/` se usa en Render. Dato del equipo: en Render cada API corre en una sola instancia. Lo que se ve en Kubernetes no dice cómo se comporta producción.
- PostgreSQL en Kubernetes es un solo pod con un volumen `ReadWriteOnce`, sin livenessProbe y sin límites de recursos. Sirve para la demo, no como base de datos de verdad.
- `/actuator/prometheus` está en `seguridad.rutas-publicas` de las tres APIs, así que no pide token. Hoy en Render no se expone porque `METRICS_EXPOSURE` no está puesta, pero si alguien la pone allá, las métricas quedan públicas.
- Cada imagen compila y corre las pruebas de su API dentro del `docker build`, así que la primera vez el Compose tarda varios minutos en construir. Docker Desktop necesita al menos 6 GB de memoria asignados, y por eso la guía apaga las APIs del Compose antes de levantar las de Kubernetes.
