# Práctica 2 - Móviles

App Android (Kotlin + Compose) con backend Flask dockerizado.

## Cómo ejecutar (en cualquier PC)

### 1. Backend

```bash
cd backend
cp .env.example .env    # en Windows PowerShell: copy .env.example .env
docker compose up -d --build
```

Verifica que quedó arriba: `docker compose ps` (o abre `http://127.0.0.1:5000/` en el navegador).

> El archivo `.env` no está en el repositorio (por seguridad, ver `.gitignore`).
> Sin este paso, `docker compose up` falla porque no encuentra el `.env`.

### 2. App Android

1. Abre la carpeta `android/` en Android Studio.
2. Conecta un dispositivo físico con depuración USB activada, **o** inicia un emulador (AVD) desde Android Studio.
3. Presiona Run (▶).

No hace falta editar ninguna IP. `app/build.gradle.kts` define una tarea Gradle
(`adbReverse`) que se ejecuta automáticamente antes de cada build de debug y
corre `adb reverse tcp:5000 tcp:5000` sobre todos los dispositivos/emuladores
conectados. Eso hace que `127.0.0.1:5000` dentro del dispositivo/emulador
apunte al backend que corre en `127.0.0.1:5000` del equipo host, sin importar
la red o la máquina de cada persona.

**Requisito:** el backend (Docker) y el dispositivo/emulador deben correr en
la **misma máquina** (esto es justamente lo que resuelve `adb reverse`; no
sirve para conectar un celular desde otra computadora o red).

## Troubleshooting

- Si el registro de usuario falla con "No se pudo conectar con el servidor":
  1. Confirma que el contenedor está arriba: `docker compose ps` (dentro de `backend/`).
  2. Confirma que el reverse está aplicado: `adb reverse --list` debe mostrar
     `tcp:5000 tcp:5000`. Si está vacío, corre manualmente
     `adb reverse tcp:5000 tcp:5000` o vuelve a compilar la app desde Android Studio.
