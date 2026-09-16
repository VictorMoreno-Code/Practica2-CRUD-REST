# Práctica 2: Aplicación móvil básica para operaciones CRUD con un servicio REST

---

## 1. Portada

| | |
|---|---|
| **Instituto** | Instituto Politécnico Nacional (IPN) |
| **Escuela** | Escuela Superior de Cómputo (ESCOM) |
| **Programa académico** | Ingeniería en Sistemas Computacionales — Plan 2020 |
| **Unidad de aprendizaje** | Desarrollo de aplicaciones móviles nativas |
| **Práctica** | Práctica 2: Aplicación móvil básica para operaciones CRUD con un servicio REST |
| **Alumno** | Moreno López Victor Eduardo |
| **Número de boleta** | `__________________` <!-- TODO: escribe aquí tu boleta --> |
| **Grupo** | 7CV4 |
| **Profesor** | `__________________` <!-- TODO: escribe aquí el nombre del profesor --> |
| **Fecha de entrega** | 18 de septiembre de 2026 |
| **Repositorio** | https://github.com/VictorMoreno-Code/Practica2-CRUD-REST |

---

## 2. Introducción

### 2.1 ¿Qué se construyó?

Este repositorio contiene **dos aplicaciones que trabajan juntas**:

1. **`backend/`** — Un servicio **REST dockerizado** escrito en Python con Flask. Expone
   una API sobre el recurso **Tarea (`Task`)** con las cuatro operaciones CRUD, más los
   endpoints de **registro** e **inicio de sesión**. Guarda la información en una base de
   datos SQLite a través de un ORM y protege las contraseñas con hash bcrypt.

2. **`android/`** — Una aplicación **Android nativa** en Kotlin con Jetpack Compose
   (Material 3). Tiene un menú de navegación con las opciones *Inicio de Sesión*,
   *Registro de Usuario* y *Operaciones CRUD*, y consume la API anterior mediante
   Retrofit + OkHttp.

La idea central es la separación de responsabilidades: el teléfono **no toca la base de
datos ni conoce las contraseñas**; solo envía peticiones HTTP con JSON y muestra lo que
el servidor responde. Toda la lógica de negocio, validación y seguridad vive en el
backend.

### 2.2 Lógica general de la aplicación

El flujo completo de la aplicación es el siguiente:

```
┌──────────────────────┐        HTTP + JSON        ┌───────────────────────────┐
│  App Android         │  ───────────────────────► │  API Flask (contenedor)   │
│  (Kotlin + Compose)  │                           │                           │
│                      │  POST /register           │  bcrypt → guarda el HASH  │
│  1. Registro         │ ◄───── 201 Creado ─────── │                           │
│                      │                           │                           │
│  2. Login            │  POST /login              │  verifica hash            │
│                      │ ◄── 200 + access_token ── │  firma un JWT             │
│                      │                           │                           │
│  3. CRUD             │  GET/POST/PUT/DELETE      │  @jwt_required()          │
│     (guarda el token)│  Authorization: Bearer …  │  filtra por user_id       │
│                      │ ◄── 200/201/400/404 ───── │  SQLAlchemy → SQLite      │
└──────────────────────┘                           └───────────────────────────┘
                                                              │
                                                     volumen ./data (persiste)
```

1. El usuario **se registra**. La app manda `username` y `password` en JSON. El servidor
   **nunca guarda la contraseña**: genera un hash bcrypt (que ya incluye su propia sal) y
   guarda únicamente ese hash.
2. El usuario **inicia sesión**. El servidor compara la contraseña recibida contra el hash
   almacenado y, si coincide, devuelve un **token JWT firmado con expiración**.
3. La app guarda ese token en memoria y lo manda en la cabecera
   `Authorization: Bearer <token>` en **todas** las peticiones del CRUD.
4. Cada endpoint del CRUD está decorado con `@jwt_required()`. Si el token falta, es
   inválido o expiró, el servidor responde **401** y la app muestra el mensaje de error.
5. Además, las consultas filtran por `user_id`, así que **cada usuario solo ve y modifica
   sus propias tareas**.

### 2.3 Stack elegido y justificación

| Capa | Tecnología | ¿Por qué esta y no otra? |
|---|---|---|
| Backend | **Flask 3** | Micro-framework: el archivo `app.py` completo cabe en una sola lectura, sin capas de configuración que escondan lo que realmente pasa. Para una API de 7 endpoints, Django o Spring Boot aportarían estructura que aquí no se necesita. |
| ORM | **Flask-SQLAlchemy** | Permite declarar las tablas como clases de Python (`User`, `Task`) y trabajar con objetos en lugar de escribir SQL a mano, lo que elimina de raíz el riesgo de inyección SQL por concatenación de cadenas. |
| Base de datos | **SQLite** | No requiere un servidor de base de datos aparte ni un segundo contenedor: la base entera es un archivo. Eso hace que el proyecto levante con un solo comando en cualquier equipo, que es justo el criterio de evaluación. |
| Contraseñas | **Flask-Bcrypt** | bcrypt es una función de hash *lenta y con sal automática*, diseñada específicamente para contraseñas. A diferencia de SHA-256, está pensada para resistir ataques de fuerza bruta con GPU. |
| Sesiones | **Flask-JWT-Extended** | Genera tokens firmados con expiración. Al ser *stateless*, el servidor no guarda sesiones en memoria, así que el contenedor puede reiniciarse sin romper el modelo, y el token viaja en una cabecera, no en una cookie (inmune a CSRF). |
| Contenedores | **Docker + Docker Compose** | Reproducibilidad: quien revise la práctica no necesita instalar Python, ni pip, ni resolver versiones. |
| App móvil | **Kotlin + Jetpack Compose (Material 3)** | Es el toolkit de UI recomendado oficialmente por Google para Android nativo. La UI declarativa hace que los estados (cargando / error / sesión iniciada) sean explícitos en el código. |
| Cliente HTTP | **Retrofit 2 + OkHttp + Gson** | Retrofit convierte la interfaz `ApiService` en llamadas HTTP reales y Gson serializa/deserializa el JSON automáticamente. El uso de `suspend fun` integra las llamadas con corrutinas, de modo que la red nunca bloquea el hilo principal. |

### 2.4 Relación con el repositorio de ejemplo

El repositorio de ejemplo `gabrielhuav/Flask-Compose-Login-API` se utilizó **únicamente
como referencia conceptual** (estructura general de un backend Flask dockerizado). **Este
proyecto no es un fork ni una copia**: el backend, el modelo `Task`, todo el CRUD, la
autenticación con JWT y la aplicación Android completa fueron escritos desde cero para
esta práctica, como puede verificarse en el historial de commits del repositorio.

---

## 3. Desarrollo

### 3.1 Conceptos fundamentales

#### Docker

Docker es una plataforma que **empaqueta una aplicación junto con todo lo que necesita
para ejecutarse** —el intérprete, las librerías, los archivos de configuración— dentro de
una unidad aislada llamada *contenedor*. La diferencia con una máquina virtual es que el
contenedor **no lleva un sistema operativo completo adentro**: comparte el núcleo (kernel)
del sistema anfitrión y solo aísla el espacio de nombres, los procesos y el sistema de
archivos. Por eso pesa megabytes en lugar de gigabytes y arranca en segundos en lugar de
minutos.

La ventaja práctica para esta entrega es la **reproducibilidad**: el revisor no necesita
tener instalado Python 3.11, ni Flask, ni la versión exacta de SQLAlchemy. Solo necesita
Docker, y el proyecto se ejecuta exactamente igual en su máquina que en la mía.

#### Imagen y contenedor

Son dos cosas distintas que suelen confundirse:

- La **imagen** es la *plantilla inmutable*: un paquete de solo lectura, construido por
  capas, que contiene el sistema de archivos ya listo (Python instalado, dependencias
  instaladas, mi código copiado). Una vez construida, no cambia. En este proyecto la
  imagen se llama `practica2-tasks-api`.
- El **contenedor** es la *instancia en ejecución* de esa imagen: un proceso vivo con su
  propia red y su propia capa de escritura encima de la imagen. En este proyecto el
  contenedor se llama `tasks-api`.

La analogía habitual es la de programación orientada a objetos: la imagen es la **clase**
y el contenedor es el **objeto** instanciado. De una misma imagen se pueden lanzar muchos
contenedores.

Lo importante es que **el contenedor es efímero**: todo lo que escriba en su capa interna
desaparece cuando se elimina. Por eso, la información que debe sobrevivir —en este caso el
archivo de la base de datos SQLite— se guarda en un **volumen**, que es una carpeta del
equipo anfitrión montada dentro del contenedor.

#### Dockerfile

Es un archivo de texto con las **instrucciones paso a paso** que Docker ejecuta para
construir la imagen. Cada instrucción genera una capa nueva, y Docker guarda esas capas en
caché: si una capa no cambió, no se vuelve a construir.

#### docker-compose.yml

Es un archivo en formato **YAML** que describe la aplicación completa como un conjunto de
**servicios**, indicando para cada uno su imagen o build, sus puertos publicados, sus
volúmenes, sus variables de entorno, sus redes y su política de reinicio. Su valor está en
que todo el entorno se levanta o se detiene con **un solo comando**
(`docker compose up` / `docker compose down`), sin tener que recordar una línea larguísima
de `docker run` con quince banderas.

#### Backend o servicio REST

Es el programa que corre **del lado del servidor** y expone la lógica de negocio a través
de rutas accesibles por HTTP. Recibe peticiones con los verbos **GET** (leer), **POST**
(crear), **PUT** (actualizar) y **DELETE** (borrar), valida lo que le mandan, consulta o
modifica la base de datos y responde en **JSON** acompañado del **código de estado HTTP**
que corresponde (`200` OK, `201` Creado, `400` Petición inválida, `401` No autorizado,
`404` No encontrado).

En esta API el diseño es *RESTful*: el recurso es `/tasks` y lo que determina la acción no
es la URL sino el **verbo HTTP** que se use sobre ella.

#### ORM y base de datos

Un **ORM** (*Object-Relational Mapper*) es una capa que traduce entre el mundo de los
objetos del lenguaje y el mundo de las tablas relacionales. Con SQLAlchemy, la clase
`Task` **es** la tabla `tasks`, cada atributo es una columna y cada instancia es una fila.
Escribir `Task.query.filter_by(user_id=3).all()` genera por debajo el
`SELECT * FROM tasks WHERE user_id = 3`, sin concatenar cadenas de SQL.

La base de datos utilizada es **SQLite**, que guarda todas las tablas en un único archivo
local (`data/app.db`). No hace falta un proceso servidor aparte, lo cual encaja
perfectamente con el requisito de que el proyecto levante en un equipo que solo tenga
Docker instalado.

---

### 3.2 Estructura del repositorio

```
Practica2_Moviles/
├── README.md                  ← este documento
├── .gitignore                 ← reglas generales (IDE, caché, secretos)
├── docs/                      ← capturas de pantalla de la ejecución
│
├── backend/                   ← SERVICIO REST DOCKERIZADO
│   ├── app.py                 ← toda la API: modelos, auth y CRUD
│   ├── requirements.txt       ← dependencias de Python con versión fija
│   ├── Dockerfile             ← receta para construir la imagen
│   ├── docker-compose.yml     ← orquestación del servicio
│   ├── .dockerignore          ← qué NO se copia a la imagen
│   ├── .env.example           ← nombres de las variables de entorno (SIN valores reales)
│   └── .gitignore             ← ignora .env, data/ y la base de datos
│
└── android/                   ← APLICACIÓN MÓVIL NATIVA
    ├── app/build.gradle.kts   ← dependencias + tarea automática `adbReverse`
    ├── gradle/libs.versions.toml
    └── app/src/main/
        ├── AndroidManifest.xml            ← permiso INTERNET y cleartext traffic
        └── java/.../aplicacionmovilbasica/
            ├── MainActivity.kt            ← UI completa en Compose (menú, login, registro, CRUD)
            ├── ApiService.kt              ← interfaz Retrofit: los 7 endpoints
            ├── Models.kt                  ← data classes de petición y respuesta
            ├── RetrofitClient.kt          ← cliente HTTP + resolución automática del host
            └── ui/theme/                  ← tema Material 3
```

---

### 3.3 Documentación de los endpoints

**URL base:** `http://<host>:5000/`

Todas las respuestas son **JSON**, incluidos los errores. Los endpoints marcados con 🔒
exigen la cabecera `Authorization: Bearer <access_token>`.

#### Tabla resumen

| # | Método | Ruta | 🔒 | Descripción | Éxito | Errores |
|---|---|---|---|---|---|---|
| 1 | `GET` | `/` | — | Verificación de que la API está viva | `200` | — |
| 2 | `POST` | `/register` | — | Registrar un usuario nuevo | `201` | `400` |
| 3 | `POST` | `/login` | — | Iniciar sesión y obtener el token | `200` | `400`, `401` |
| 4 | `GET` | `/tasks` | 🔒 | Leer todas las tareas del usuario | `200` | `401` |
| 5 | `POST` | `/tasks` | 🔒 | Crear una tarea | `201` | `400`, `401` |
| 6 | `PUT` | `/tasks/<id>` | 🔒 | Actualizar una tarea existente | `200` | `400`, `401`, `404` |
| 7 | `DELETE` | `/tasks/<id>` | 🔒 | Borrar una tarea | `200` | `401`, `404` |

---

#### 1. `GET /` — Verificación del servicio

**Petición**

```bash
curl http://127.0.0.1:5000/
```

**Respuesta `200 OK`**

```json
{ "status": "ok", "message": "API de tareas funcionando" }
```

---

#### 2. `POST /register` — Registro de usuario

**Parámetros del cuerpo (JSON)**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `username` | string | Sí | Único en el sistema |
| `password` | string | Sí | Mínimo 4 caracteres |

**Petición**

```bash
curl -X POST http://127.0.0.1:5000/register \
     -H "Content-Type: application/json" \
     -d '{"username":"prof","password":"1234"}'
```

**Respuesta `201 Created`**

```json
{
  "message": "Usuario registrado correctamente",
  "user": { "id": 1, "username": "prof" }
}
```

**Respuesta `400 Bad Request`** (usuario repetido)

```json
{ "error": "El nombre de usuario ya existe" }
```

> Otros mensajes con `400`: `"El campo 'username' es obligatorio"`,
> `"El campo 'password' es obligatorio"`,
> `"La contraseña debe tener al menos 4 caracteres"`.

---

#### 3. `POST /login` — Inicio de sesión

**Petición**

```bash
curl -X POST http://127.0.0.1:5000/login \
     -H "Content-Type: application/json" \
     -d '{"username":"prof","password":"1234"}'
```

**Respuesta `200 OK`**

```json
{
  "message": "Inicio de sesión exitoso",
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZXhwIjo...",
  "user": { "id": 1, "username": "prof" }
}
```

**Respuesta `401 Unauthorized`** (contraseña o usuario incorrectos)

```json
{ "error": "Credenciales inválidas" }
```

> El mensaje es **deliberadamente ambiguo**: no distingue entre "ese usuario no existe" y
> "la contraseña es incorrecta", para no revelar qué nombres de usuario están registrados.

---

#### 4. `GET /tasks` 🔒 — Leer tareas

**Petición**

```bash
curl http://127.0.0.1:5000/tasks \
     -H "Authorization: Bearer $TOKEN"
```

**Respuesta `200 OK`**

```json
{
  "tasks": [
    { "id": 1, "title": "Tarea 1", "description": "demo", "status": "pending", "user_id": 1 }
  ]
}
```

**Respuesta `401 Unauthorized`** (sin token)

```json
{ "error": "Falta el token de autorización" }
```

> Si el token existe pero está manipulado responde `{"error": "Token inválido"}`, y si ya
> venció, `{"error": "El token ha expirado, inicia sesión de nuevo"}`. Ambos con `401`.

---

#### 5. `POST /tasks` 🔒 — Crear tarea

**Parámetros del cuerpo (JSON)**

| Campo | Tipo | Obligatorio | Valor por defecto |
|---|---|---|---|
| `title` | string | Sí | — |
| `description` | string | No | `""` |
| `status` | string | No | `"pending"` |

**Petición**

```bash
curl -X POST http://127.0.0.1:5000/tasks \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $TOKEN" \
     -d '{"title":"Tarea 1","description":"demo"}'
```

**Respuesta `201 Created`**

```json
{
  "message": "Tarea creada",
  "task": { "id": 1, "title": "Tarea 1", "description": "demo", "status": "pending", "user_id": 1 }
}
```

**Respuesta `400 Bad Request`**

```json
{ "error": "El campo 'title' es obligatorio" }
```

---

#### 6. `PUT /tasks/<id>` 🔒 — Actualizar tarea

Actualización **parcial**: solo se modifican los campos que se envíen.

**Petición**

```bash
curl -X PUT http://127.0.0.1:5000/tasks/1 \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $TOKEN" \
     -d '{"title":"Tarea 1 editada","status":"done"}'
```

**Respuesta `200 OK`**

```json
{
  "message": "Tarea actualizada",
  "task": { "id": 1, "title": "Tarea 1 editada", "description": "demo", "status": "done", "user_id": 1 }
}
```

**Respuesta `404 Not Found`** (la tarea no existe **o no le pertenece al usuario del token**)

```json
{ "error": "Tarea no encontrada" }
```

---

#### 7. `DELETE /tasks/<id>` 🔒 — Borrar tarea

**Petición**

```bash
curl -X DELETE http://127.0.0.1:5000/tasks/1 \
     -H "Authorization: Bearer $TOKEN"
```

**Respuesta `200 OK`**

```json
{ "message": "Tarea eliminada" }
```

**Respuesta `404 Not Found`**

```json
{ "error": "Tarea no encontrada" }
```

---

### 3.4 Instalación y ejecución

#### Requisitos previos

| Para el backend | Para la app móvil |
|---|---|
| Docker Desktop (o Docker Engine + plugin Compose) | Android Studio (Ladybug o superior) |
| — | Un emulador (AVD) **o** un dispositivo físico con depuración USB |

#### Paso 1 — Clonar el repositorio

```bash
git clone https://github.com/VictorMoreno-Code/Practica2-CRUD-REST.git
cd Practica2-CRUD-REST
```

#### Paso 2 — Levantar el backend

```bash
cd backend
docker compose up --build
```

**Eso es todo.** No hay que crear archivos, copiar `.env` ni editar nada: el proyecto está
configurado para arrancar con valores por defecto seguros en un equipo limpio.

La consola debe mostrar algo como:

```
tasks-api  | [AVISO] JWT_SECRET_KEY no definida: se generó una llave aleatoria...
tasks-api  |  * Serving Flask app 'app'
tasks-api  |  * Running on all addresses (0.0.0.0)
tasks-api  |  * Running on http://127.0.0.1:5000
```

**Verificación rápida** (en otra terminal, o desde el navegador):

```bash
curl http://127.0.0.1:5000/
# {"status":"ok","message":"API de tareas funcionando"}

docker compose ps
# El contenedor tasks-api debe aparecer como "running (healthy)"
```

> **Opcional:** si se quiere que las sesiones sobrevivan a un reinicio del contenedor,
> basta crear un `.env` a partir del ejemplo y poner una llave fija:
> `cp .env.example .env` (en PowerShell: `copy .env.example .env`) y escribir un valor en
> `JWT_SECRET_KEY`. Ese archivo **nunca se sube al repositorio**.

#### Paso 3 — Ejecutar la aplicación Android

1. Abrir **la carpeta `android/`** (no la raíz del repositorio) en Android Studio.
2. Esperar a que termine el *Gradle Sync*.
3. Iniciar un emulador desde el Device Manager, o conectar un teléfono con depuración USB.
4. Pulsar **Run ▶**.

**No hay que editar ninguna dirección IP.** La resolución del host es automática (ver la
sección 3.6, *Decisiones técnicas*).

#### Paso 4 — Detener el entorno

```bash
docker compose down          # detiene y elimina el contenedor
docker compose down -v       # además borra los datos persistidos
```

---

### 3.5 Explicación del `Dockerfile` y del `docker-compose.yml`

#### `backend/Dockerfile`, instrucción por instrucción

| Instrucción | Qué hace |
|---|---|
| `FROM python:3.11-slim` | Define la **imagen base**: una distribución mínima de Debian con Python 3.11 ya instalado. La variante `slim` pesa mucho menos que la completa porque no trae herramientas de compilación ni documentación. |
| `ENV PYTHONDONTWRITEBYTECODE=1` | Evita que Python genere archivos `.pyc` dentro del contenedor. No sirven de nada en un contenedor efímero y solo ensucian la imagen. |
| `ENV PYTHONUNBUFFERED=1` | Obliga a Python a escribir en la salida estándar sin búfer, para que los `print` y los logs de Flask aparezcan **en tiempo real** en `docker compose logs`. |
| `WORKDIR /app` | Fija `/app` como directorio de trabajo dentro de la imagen. Las instrucciones siguientes y el proceso final se ejecutan desde ahí. |
| `COPY requirements.txt .` | Copia **solo** la lista de dependencias. Se hace antes que el resto del código a propósito: ver la fila siguiente. |
| `RUN pip install --no-cache-dir -r requirements.txt` | Instala las dependencias. Como Docker cachea cada capa, mientras `requirements.txt` no cambie **esta capa se reutiliza**, y modificar `app.py` no obliga a reinstalar Flask cada vez. `--no-cache-dir` evita guardar los paquetes descargados, reduciendo el tamaño final. |
| `COPY . .` | Copia ya sí el código fuente del proyecto a `/app`. Lo que aparece en `.dockerignore` (el `.env`, la base de datos, `__pycache__`) **queda fuera**. |
| `EXPOSE 5000` | Documenta que el proceso escucha en el puerto 5000. Es informativo: la publicación real del puerto la hace `docker-compose.yml`. |
| `CMD ["python", "app.py"]` | Comando que se ejecuta al arrancar el contenedor. Es el proceso principal: si termina, el contenedor se detiene. |

#### `backend/docker-compose.yml`, clave por clave

| Clave | Qué hace |
|---|---|
| `services:` | Lista de servicios que forman la aplicación. Aquí hay uno solo: `backend`. |
| `build: .` | Construye la imagen usando el `Dockerfile` de esta carpeta, en lugar de descargar una imagen ya hecha. |
| `image: practica2-tasks-api` | Nombre con el que se etiqueta la imagen construida. |
| `container_name: tasks-api` | Nombre fijo del contenedor, para poder hacer `docker logs tasks-api` sin adivinar un identificador aleatorio. |
| `ports: - "5000:5000"` | Publica el puerto 5000 **del contenedor** en el puerto 5000 **del equipo anfitrión**, en todas sus interfaces de red. Es lo que permite que el emulador o un teléfono de la misma red alcancen la API. |
| `environment:` | Inyecta las variables de entorno. Cada una usa la forma `${VARIABLE:-valor_por_defecto}`, de modo que si existe un `.env` en la carpeta se toman sus valores y, si no existe, se usan los valores por defecto **sin que el comando falle**. |
| `volumes: - ./data:/app/data` | Monta la carpeta `./data` del equipo anfitrión dentro del contenedor en `/app/data`. Ahí vive el archivo SQLite, así que **los usuarios y tareas sobreviven** aunque el contenedor se destruya y se vuelva a crear. |
| `healthcheck:` | Cada 10 segundos ejecuta dentro del contenedor una petición al endpoint raíz. Si responde `200`, Docker marca el servicio como *healthy*; así `docker compose ps` distingue entre "el proceso arrancó" y "la API realmente contesta". |
| `restart: unless-stopped` | Si el proceso se cae o se reinicia la computadora, Docker vuelve a levantar el contenedor automáticamente, salvo que se haya detenido a mano. |

---

### 3.6 Decisiones técnicas

#### A) Variables de entorno sin `env_file` obligatorio

**Problema.** El criterio de evaluación dice que la práctica se revisa clonando el
repositorio en un equipo limpio y ejecutando `docker compose up --build`. Pero el
criterio de seguridad exige que el `.env` **no** esté en el repositorio. Con la
configuración clásica `env_file: - .env`, Compose falla con un error si ese archivo no
existe: es decir, ambos requisitos se contradicen.

**Solución adoptada.** En lugar de `env_file`, se usa el bloque `environment:` con
**interpolación con valor por defecto**: `${JWT_SECRET_KEY:-}`. Docker Compose lee de
forma automática un archivo `.env` de la carpeta del proyecto *si existe*, y si no existe
simplemente aplica los valores por defecto. Complementariamente, `app.py` **genera una
llave JWT aleatoria** cuando la variable llega vacía, avisando por consola.

**Ventajas.** El proyecto levanta con un solo comando en un equipo limpio, y al mismo
tiempo **no hay ni un solo secreto escrito en el repositorio**.
**Desventaja.** Si no se define `JWT_SECRET_KEY`, al reiniciar el contenedor la llave
cambia y los tokens emitidos antes dejan de ser válidos: hay que volver a iniciar sesión.
Se documenta cómo evitarlo creando un `.env` propio.

#### B) Ruta absoluta para la base de datos

Flask-SQLAlchemy 3.x resuelve las rutas SQLite **relativas** contra la carpeta `instance/`
de la aplicación, **no** contra el directorio de trabajo. Una URI como
`sqlite:///data/app.db` termina apuntando a `/app/instance/data/app.db`, que **no es** la
carpeta montada como volumen: la base parecería funcionar, pero se perdería al recrear el
contenedor.

Por eso `app.py` construye siempre una **ruta absoluta** a partir de la ubicación real del
archivo, dando `/app/data/app.db` dentro del contenedor, que sí coincide con el volumen
`./data:/app/data`. La persistencia queda garantizada y verificada (ver QA, prueba 15).

#### C) Resolución automática de la dirección del backend (`127.0.0.1` / `10.0.2.2`)

**Problema.** Desde un dispositivo Android, `localhost` apunta al propio dispositivo, no a
la computadora. El enunciado propone usar `10.0.2.2:5000`, que es la dirección con la que
el **emulador** alcanza al anfitrión; pero esa dirección **no existe** en un teléfono
físico conectado por USB, y una IP de red local (`192.168.x.x`) cambia de máquina en
máquina y obliga a editar el código en cada revisión.

**Solución adoptada.** Dos mecanismos complementarios:

1. Una tarea Gradle personalizada, **`adbReverse`** (`android/app/build.gradle.kts`),
   enganchada a `preDebugBuild`, que ejecuta `adb reverse tcp:5000 tcp:5000` sobre todos
   los dispositivos conectados en cada compilación de depuración. Eso hace que
   `127.0.0.1:5000` **dentro** del dispositivo se redirija al puerto 5000 del equipo
   anfitrión, tanto en emulador como en teléfono por USB.
2. Un interceptor de OkHttp, **`HostFailoverInterceptor`** (`RetrofitClient.kt`), que
   prueba los hosts candidatos **en orden** —`127.0.0.1` y después `10.0.2.2`— y se queda
   con el primero que responda, recordándolo para las peticiones siguientes.

**Ventajas.** Funciona en emulador con o sin `adb reverse`, funciona en teléfono físico
por USB, y **nadie tiene que editar una IP** para revisar la práctica.
**Desventajas.** Si ningún host responde, la primera petición tarda el doble en fallar
(dos intentos de conexión); por eso el *connect timeout* se bajó a 5 segundos. Para un
teléfono conectado solo por Wi-Fi (sin cable USB), habría que añadir la IP local del
equipo como primer elemento de `CANDIDATE_HOSTS`, lo cual está documentado en el propio
archivo.

#### D) JWT en lugar de sesiones con cookie

Se eligió un **token firmado con expiración** en vez de una sesión de servidor porque: es
*stateless* (el contenedor puede reiniciarse sin invalidar el modelo de sesión), viaja en
una cabecera `Authorization` en lugar de una cookie automática (lo que lo hace inmune a
CSRF), y es el mecanismo estándar para clientes móviles, que no tienen un gestor de
cookies como el navegador.

---

### 3.7 Proceso de QA (pruebas realizadas)

Pruebas ejecutadas contra la API con `curl` y desde la aplicación, verificando **código de
estado** y **cuerpo de la respuesta**.

| # | Caso de prueba | Esperado | Resultado |
|---|---|---|---|
| 1 | `GET /` con el servicio arriba | `200` + `{"status":"ok"}` | ✅ |
| 2 | Registro de usuario nuevo | `201` + datos del usuario | ✅ |
| 3 | Registro con un `username` ya existente | `400` + `"El nombre de usuario ya existe"` | ✅ |
| 4 | Registro sin `password` | `400` | ✅ |
| 5 | Registro con contraseña de menos de 4 caracteres | `400` | ✅ |
| 6 | Login con credenciales correctas | `200` + `access_token` | ✅ |
| 7 | Login con contraseña incorrecta | `401` + `"Credenciales inválidas"` | ✅ |
| 8 | `GET /tasks` **sin** cabecera `Authorization` | `401` + `"Falta el token de autorización"` | ✅ |
| 9 | `GET /tasks` con un token manipulado | `401` + `"Token inválido"` | ✅ |
| 10 | `POST /tasks` con token válido | `201` + tarea creada | ✅ |
| 11 | `POST /tasks` sin `title` | `400` | ✅ |
| 12 | `PUT /tasks/<id>` sobre una tarea propia | `200` + tarea actualizada | ✅ |
| 13 | `PUT /tasks/999` (inexistente) | `404` + `"Tarea no encontrada"` | ✅ |
| 14 | `DELETE /tasks/<id>` sobre una tarea propia | `200` + `"Tarea eliminada"` | ✅ |
| 15 | Persistencia: `docker compose down` + `up` y volver a iniciar sesión | El usuario y sus tareas siguen existiendo | ✅ |
| 16 | Aislamiento: usuario B intenta leer/editar tareas de usuario A | No las ve; `PUT`/`DELETE` responden `404` | ✅ |
| 17 | Inspección de la tabla `users` en la base de datos | La columna `password_hash` contiene un hash bcrypt (`$2b$12$…`), **nunca** texto plano | ✅ |
| 18 | Ruta inexistente (`GET /noexiste`) | `404` en **JSON**, no una página HTML de Flask | ✅ |
| 19 | Búsqueda de secretos en el repositorio (`git ls-files`) | No hay `.env`, ni `.db`, ni llaves versionadas | ✅ |

---

### 3.8 Capturas de pantalla de la ejecución

> **Instrucciones para completar esta sección:** toma cada captura, guárdala en la carpeta
> `docs/` con **exactamente** el nombre de archivo indicado y las imágenes aparecerán solas
> en este documento. Borra este párrafo cuando termines.

#### A. Backend dockerizado

**Captura 01 — `docs/01-docker-compose-up.png`**
Terminal ejecutando `docker compose up --build` desde la carpeta `backend/`, mostrando la
construcción de la imagen y las líneas finales `Serving Flask app 'app'` y
`Running on all addresses (0.0.0.0)`.

![Backend levantado con docker compose up --build](docs/01-docker-compose-up.png)

**Captura 02 — `docs/02-docker-compose-ps.png`**
Salida de `docker compose ps` mostrando el contenedor `tasks-api` en estado
`running (healthy)` y el puerto `0.0.0.0:5000->5000/tcp`.

![Estado del contenedor](docs/02-docker-compose-ps.png)

**Captura 03 — `docs/03-endpoint-raiz.png`**
Navegador (o `curl`) apuntando a `http://127.0.0.1:5000/` y mostrando el JSON
`{"status":"ok","message":"API de tareas funcionando"}`.

![Verificación del endpoint raíz](docs/03-endpoint-raiz.png)

#### B. Navegación de la aplicación

**Captura 04 — `docs/04-menu-navegacion.png`**
Aplicación abierta con el **menú desplegable de la barra superior desplegado**, mostrando
las tres opciones: *Inicio de Sesión*, *Registro de Usuario* y *Operaciones CRUD*.

![Menú de navegación](docs/04-menu-navegacion.png)

#### C. Registro de usuario

**Captura 05 — `docs/05-registro-formulario.png`**
Pantalla *Registro de Usuario* con el formulario lleno (usuario escrito y contraseña
enmascarada con puntos), **antes** de pulsar *Registrarme*.

![Formulario de registro](docs/05-registro-formulario.png)

**Captura 06 — `docs/06-registro-exitoso.png`**
Momento en que aparece el Toast **"Usuario registrado correctamente"** (respuesta `201`).

![Registro exitoso](docs/06-registro-exitoso.png)

**Captura 07 — `docs/07-registro-usuario-duplicado.png`**
Intento de registrar **el mismo nombre de usuario otra vez**, mostrando el Toast
**"El nombre de usuario ya existe"** (respuesta `400`).

![Registro con usuario duplicado](docs/07-registro-usuario-duplicado.png)

#### D. Inicio de sesión y manejo de credenciales incorrectas

**Captura 08 — `docs/08-login-formulario.png`**
Pantalla *Inicio de Sesión* con las credenciales capturadas.

![Formulario de login](docs/08-login-formulario.png)

**Captura 09 — `docs/09-login-credenciales-incorrectas.png`** ⚠️ *(requerida explícitamente por el enunciado)*
Intento de iniciar sesión con **contraseña incorrecta**, mostrando el Toast
**"Credenciales inválidas"** que proviene de la respuesta `401` del servidor.

![Manejo de credenciales incorrectas](docs/09-login-credenciales-incorrectas.png)

**Captura 10 — `docs/10-login-exitoso.png`**
Inicio de sesión correcto: Toast **"Inicio de sesión exitoso"** y navegación automática a
la pantalla *Operaciones CRUD*.

![Login exitoso](docs/10-login-exitoso.png)

**Captura 11 — `docs/11-crud-sin-sesion.png`**
Intento de entrar a *Operaciones CRUD* desde el menú **sin haber iniciado sesión**,
mostrando el Toast **"Debes iniciar sesión antes de ver tus tareas"**.

![Acceso protegido sin sesión](docs/11-crud-sin-sesion.png)

#### E. Las cuatro operaciones CRUD

**Captura 12 — `docs/12-read-lista-vacia.png`** *(READ / `GET /tasks`)*
Pantalla CRUD recién abierta con el mensaje **"No tienes tareas todavía. Usa el botón +
para crear una."**

![Lectura inicial sin tareas](docs/12-read-lista-vacia.png)

**Captura 13 — `docs/13-create-dialogo.png`** *(CREATE / `POST /tasks`)*
Diálogo **"Nueva tarea"** abierto con el título y la descripción capturados, antes de
pulsar *Crear*.

![Diálogo de creación](docs/13-create-dialogo.png)

**Captura 14 — `docs/14-create-resultado.png`** *(CREATE / `POST /tasks`)*
Lista mostrando **al menos dos tareas ya creadas** y el Toast **"Tarea creada"**
(respuesta `201`).

![Tareas creadas y listadas](docs/14-create-resultado.png)

**Captura 15 — `docs/15-update-dialogo.png`** *(UPDATE / `PUT /tasks/<id>`)*
Diálogo **"Editar tarea"** abierto con el texto ya modificado, antes de pulsar *Guardar*.

![Diálogo de edición](docs/15-update-dialogo.png)

**Captura 16 — `docs/16-update-resultado.png`** *(UPDATE / `PUT /tasks/<id>`)*
Lista mostrando la tarea **ya actualizada** y el Toast **"Tarea actualizada"**
(respuesta `200`).

![Tarea actualizada](docs/16-update-resultado.png)

**Captura 17 — `docs/17-delete-resultado.png`** *(DELETE / `DELETE /tasks/<id>`)*
Lista **después de eliminar** una tarea, con el Toast **"Tarea eliminada"**
(respuesta `200`).

![Tarea eliminada](docs/17-delete-resultado.png)

#### F. Evidencia técnica

**Captura 18 — `docs/18-logcat-authorization.png`**
Ventana **Logcat** de Android Studio con el log de OkHttp, donde se alcanza a leer la
cabecera `Authorization: Bearer eyJ…` de una petición a `/tasks` y la respuesta
`200 OK` con su JSON.

![Logcat mostrando el token en la cabecera](docs/18-logcat-authorization.png)

**Captura 19 — `docs/19-password-hash.png`**
Contenido de la tabla `users` de la base de datos (con *DB Browser for SQLite*, o con
`docker exec tasks-api python -c "..."`), evidenciando que la columna `password_hash`
guarda un hash **`$2b$12$...`** y **nunca** la contraseña en texto plano.

![Contraseñas almacenadas como hash bcrypt](docs/19-password-hash.png)

**Captura 20 — `docs/20-persistencia.png`**
Prueba de persistencia: terminal con `docker compose down` seguido de
`docker compose up -d`, y a continuación la app volviendo a iniciar sesión con el **mismo
usuario** y mostrando **las tareas que ya existían**.

![Persistencia de los datos tras recrear el contenedor](docs/20-persistencia.png)

---

## 4. Conclusiones

### Retos y cómo se resolvieron

**1. La conexión entre el dispositivo y el backend fue el problema más difícil.**
El obstáculo no era programar el CRUD, sino que `localhost` significa cosas distintas
dentro del emulador y fuera de él. La primera versión usaba una IP fija de la red local,
que dejaba de funcionar en cuanto cambiaba de red. La solución final combina `adb reverse`
—automatizado en una tarea de Gradle para que nadie tenga que acordarse de ejecutarlo— con
un interceptor de OkHttp que prueba varios hosts y se queda con el que responde. El
aprendizaje fue que **la configuración de red también es parte del diseño de la app**, no
un detalle de última hora.

**2. La base de datos parecía persistir, pero no lo hacía.**
El volumen de Docker estaba correctamente declarado y aun así la carpeta `./data` seguía
vacía. La causa resultó ser que Flask-SQLAlchemy 3.x resuelve las rutas SQLite relativas
contra la carpeta `instance/` y no contra el directorio de trabajo, así que el archivo se
estaba creando en otro lugar. Se corrigió construyendo la ruta absoluta desde el propio
`app.py`. El aprendizaje: **que un dato se guarde no significa que se guarde donde uno
cree**, y conviene verificar la persistencia destruyendo el contenedor a propósito.

**3. Hacer que el secreto no estuviera en el repositorio y que aun así todo levantara con
un solo comando.** Las dos exigencias parecían incompatibles, porque `env_file` obliga a
que el archivo exista. Se resolvió con interpolación con valores por defecto en Compose y
generación de una llave aleatoria en el arranque. El aprendizaje: **la seguridad y la
facilidad de despliegue no tienen por qué estar reñidas**, pero conciliarlas obliga a
entender bien la herramienta.

**4. Devolver siempre JSON, incluso en los errores.**
Al principio, un JSON mal formado enviado desde la app provocaba que Flask respondiera con
una página HTML de error, y el cliente reventaba al intentar parsearla. Se resolvió
registrando manejadores globales (`@app.errorhandler`) para `400`, `404`, `405` y `500`, y
leyendo el cuerpo con `get_json(silent=True)`. El aprendizaje: **una API debe mantener su
contrato también cuando falla**.

### Logros

- Las **cuatro operaciones CRUD** funcionando de extremo a extremo desde una app nativa.
- Un sistema de autenticación con **hash bcrypt con sal** y **tokens JWT con expiración**,
  con todos los endpoints del CRUD protegidos y aislamiento por usuario.
- Un backend que levanta con **un solo comando** en un equipo que solo tenga Docker, sin
  pasos previos ni configuración manual.
- Una UI que **comunica su estado en todo momento**: indicadores de carga, campos
  deshabilitados mientras hay una petición en curso, y mensajes de error que vienen del
  servidor y no genéricos.
- Cero secretos y cero bases de datos versionadas en el repositorio.

---

## 5. Bibliografía

Docker Inc. (2026). *Docker Compose specification*. Docker Documentation.
https://docs.docker.com/reference/compose-file/

Docker Inc. (2026). *Dockerfile reference*. Docker Documentation.
https://docs.docker.com/reference/dockerfile/

Google. (2026). *Connect to the network* [Android Developers]. https://developer.android.com/develop/connectivity/network-ops/connecting

Google. (2026). *Jetpack Compose UI App Development Toolkit*. Android Developers.
https://developer.android.com/compose

Google. (2026). *Material Design 3 for Compose*. Android Developers.
https://developer.android.com/develop/ui/compose/designsystems/material3

Google. (2026). *Run apps on the Android Emulator: Set up Android Emulator networking*. Android Developers. https://developer.android.com/studio/run/emulator-networking

Grinberg, M. (2018). *Flask web development: Developing web applications with Python*
(2a ed.). O'Reilly Media.

Internet Engineering Task Force. (2015). *RFC 7519: JSON Web Token (JWT)*.
https://datatracker.ietf.org/doc/html/rfc7519

Internet Engineering Task Force. (2022). *RFC 9110: HTTP Semantics*.
https://datatracker.ietf.org/doc/html/rfc9110

Pallets Projects. (2026). *Flask documentation (3.x)*. https://flask.palletsprojects.com/

Pallets Projects. (2026). *Flask-SQLAlchemy documentation*.
https://flask-sqlalchemy.palletsprojects.com/

Provos, N., & Mazières, D. (1999). A future-adaptable password scheme. *Proceedings of the
USENIX Annual Technical Conference*, 81–91.

Square, Inc. (2026). *Retrofit: A type-safe HTTP client for Android and Java*.
https://square.github.io/retrofit/

Square, Inc. (2026). *OkHttp: An HTTP client for the JVM and Android*.
https://square.github.io/okhttp/

Vergara, A. (2026). *Flask-JWT-Extended documentation*.
https://flask-jwt-extended.readthedocs.io/
