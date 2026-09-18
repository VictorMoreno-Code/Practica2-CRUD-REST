"""
API REST para gestión de tareas (Task) con autenticación de usuarios.

Stack:
- Flask: framework web principal.
- Flask-SQLAlchemy: ORM para persistir usuarios y tareas en SQLite.
- Flask-Bcrypt: hasheo seguro de contraseñas (con sal) antes de guardarlas.
- Flask-JWT-Extended: generación y validación de tokens JWT con expiración,
  usados como mecanismo de "sesión segura" sin estado en el servidor.

Todas las rutas devuelven SIEMPRE JSON (incluso los errores 400/404/500),
para que un cliente Android que hace un parseo estricto del cuerpo de la
respuesta nunca reciba HTML ni provoque una excepción de parseo.
"""

import os
import secrets
from datetime import timedelta

from dotenv import load_dotenv
from flask import Flask, request, jsonify
from flask_sqlalchemy import SQLAlchemy
from flask_bcrypt import Bcrypt
from flask_jwt_extended import (
    JWTManager,
    create_access_token,
    jwt_required,
    get_jwt_identity,
)

# ---------------------------------------------------------------------------
# Configuración inicial
# ---------------------------------------------------------------------------

# Carga las variables definidas en un archivo .env local (útil cuando se
# ejecuta sin Docker). Dentro del contenedor las variables ya llegan
# inyectadas por docker-compose, pero llamar a load_dotenv() no estorba.
load_dotenv()

BASE_DIR = os.path.abspath(os.path.dirname(__file__))

app = Flask(__name__)

# ---------------------------------------------------------------------------
# Base de datos
# ---------------------------------------------------------------------------
# IMPORTANTE: Flask-SQLAlchemy 3.x resuelve las rutas SQLite RELATIVAS
# (p. ej. "sqlite:///data/app.db") contra la carpeta "instance/" de la
# aplicación, NO contra el directorio de trabajo. Por eso aquí se construye
# siempre una ruta ABSOLUTA: así el archivo de la base cae exactamente en
# /app/data/app.db dentro del contenedor, que es la carpeta montada como
# volumen en docker-compose.yml y, por lo tanto, la que realmente persiste.
DEFAULT_DB_PATH = os.path.join(BASE_DIR, "data", "app.db")
DATABASE_URL = os.getenv("DATABASE_URL") or "sqlite:///" + DEFAULT_DB_PATH.replace(
    os.sep, "/"
)

# Se crea la carpeta destino antes de que SQLAlchemy intente abrir el archivo.
if DATABASE_URL.startswith("sqlite:///"):
    db_file = DATABASE_URL.replace("sqlite:///", "", 1)
    os.makedirs(os.path.dirname(db_file) or ".", exist_ok=True)

app.config["SQLALCHEMY_DATABASE_URI"] = DATABASE_URL
app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False

# ---------------------------------------------------------------------------
# Llave de firma de los tokens (sesión segura)
# ---------------------------------------------------------------------------
# Ningún secreto está escrito en el código ni subido al repositorio. Si la
# variable de entorno JWT_SECRET_KEY no viene definida, se genera una llave
# aleatoria para esta ejecución: el proyecto levanta sin configuración previa
# (basta "docker compose up --build") y aun así no hay secretos versionados.
# El costo es que, al reiniciar el contenedor, los tokens emitidos antes
# dejan de ser válidos y hay que iniciar sesión de nuevo; para evitarlo basta
# definir JWT_SECRET_KEY en un archivo .env (ver .env.example).
JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY") or ""
if not JWT_SECRET_KEY:
    JWT_SECRET_KEY = secrets.token_hex(32)
    print(
        "[AVISO] JWT_SECRET_KEY no definida: se generó una llave aleatoria "
        "solo para esta ejecución. Define JWT_SECRET_KEY en backend/.env si "
        "quieres que las sesiones sobrevivan a un reinicio del contenedor.",
        flush=True,
    )

app.config["JWT_SECRET_KEY"] = JWT_SECRET_KEY

# Tiempo de expiración del token de acceso, configurable por variable de
# entorno. Esto es lo que hace que la "sesión" tenga un límite de tiempo.
try:
    jwt_expire_minutes = int(os.getenv("JWT_ACCESS_TOKEN_EXPIRES_MINUTES") or 60)
except ValueError:
    jwt_expire_minutes = 60
app.config["JWT_ACCESS_TOKEN_EXPIRES"] = timedelta(minutes=jwt_expire_minutes)

db = SQLAlchemy(app)
bcrypt = Bcrypt(app)
jwt = JWTManager(app)


# ---------------------------------------------------------------------------
# Modelos
# ---------------------------------------------------------------------------

class User(db.Model):
    """Representa a un usuario registrado en el sistema."""

    __tablename__ = "users"

    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(80), unique=True, nullable=False)
    # Solo se guarda el HASH de la contraseña (con sal incluida), nunca el
    # texto plano.
    password_hash = db.Column(db.String(255), nullable=False)

    tasks = db.relationship(
        "Task", backref="owner", lazy=True, cascade="all, delete-orphan"
    )

    def set_password(self, raw_password: str) -> None:
        """Genera y guarda el hash bcrypt (con sal) de la contraseña."""
        self.password_hash = bcrypt.generate_password_hash(raw_password).decode(
            "utf-8"
        )

    def check_password(self, raw_password: str) -> bool:
        """Verifica una contraseña en texto plano contra el hash guardado."""
        return bcrypt.check_password_hash(self.password_hash, raw_password)

    def to_dict(self) -> dict:
        return {"id": self.id, "username": self.username}


class Task(db.Model):
    """Recurso sobre el que se implementan las operaciones CRUD."""

    __tablename__ = "tasks"

    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(120), nullable=False)
    description = db.Column(db.String(500), nullable=True, default="")
    status = db.Column(db.String(20), nullable=False, default="pending")
    # Llave foránea: cada tarea pertenece exactamente a un usuario.
    user_id = db.Column(db.Integer, db.ForeignKey("users.id"), nullable=False)

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "title": self.title,
            "description": self.description,
            "status": self.status,
            "user_id": self.user_id,
        }


with app.app_context():
    db.create_all()


# ---------------------------------------------------------------------------
# Manejo global de errores: TODO responde en JSON, nunca en HTML.
# Esto es lo que evita que la app de Android reciba una página de error de
# Flask/Werkzeug (HTML) y truene al intentar hacer JSON.parse() sobre ella.
# ---------------------------------------------------------------------------

@app.errorhandler(400)
def bad_request(_error):
    return jsonify({"error": "Solicitud inválida"}), 400


@app.errorhandler(404)
def not_found(_error):
    return jsonify({"error": "Recurso no encontrado"}), 404


@app.errorhandler(405)
def method_not_allowed(_error):
    return jsonify({"error": "Método no permitido"}), 405


@app.errorhandler(500)
def internal_error(_error):
    return jsonify({"error": "Error interno del servidor"}), 500


@jwt.unauthorized_loader
def missing_token_callback(_reason):
    return jsonify({"error": "Falta el token de autorización"}), 401


@jwt.invalid_token_loader
def invalid_token_callback(_reason):
    return jsonify({"error": "Token inválido"}), 401


@jwt.expired_token_loader
def expired_token_callback(_jwt_header, _jwt_payload):
    return jsonify({"error": "El token ha expirado, inicia sesión de nuevo"}), 401


def get_json_body() -> dict:
    """
    Obtiene el cuerpo JSON de la petición de forma segura.

    Se usa silent=True para que Flask NUNCA lance una excepción si el
    cliente Android manda un JSON mal formado, vacío o con un
    Content-Type incorrecto; en su lugar se devuelve un diccionario vacío
    y cada ruta valida los campos que realmente necesita, respondiendo
    400 de forma controlada en vez de tronar con un error 500.
    """
    return request.get_json(silent=True) or {}


def get_current_user_id() -> int:
    """Convierte la identidad guardada en el JWT (string) al id entero."""
    return int(get_jwt_identity())


# ---------------------------------------------------------------------------
# Endpoint de verificación
# ---------------------------------------------------------------------------

@app.route("/", methods=["GET"])
def health_check():
    return jsonify({"status": "ok", "message": "API de tareas funcionando"}), 200


# ---------------------------------------------------------------------------
# Autenticación
# ---------------------------------------------------------------------------

@app.route("/register", methods=["POST"])
def register():
    data = get_json_body()
    username = data.get("username")
    password = data.get("password")

    if not username or not isinstance(username, str):
        return jsonify({"error": "El campo 'username' es obligatorio"}), 400
    if not password or not isinstance(password, str):
        return jsonify({"error": "El campo 'password' es obligatorio"}), 400
    if len(password) < 4:
        return (
            jsonify({"error": "La contraseña debe tener al menos 4 caracteres"}),
            400,
        )

    if User.query.filter_by(username=username).first() is not None:
        return jsonify({"error": "El nombre de usuario ya existe"}), 400

    new_user = User(username=username)
    new_user.set_password(password)

    db.session.add(new_user)
    db.session.commit()

    return (
        jsonify(
            {"message": "Usuario registrado correctamente", "user": new_user.to_dict()}
        ),
        201,
    )


@app.route("/login", methods=["POST"])
def login():
    data = get_json_body()
    username = data.get("username")
    password = data.get("password")

    if not username or not password:
        return jsonify({"error": "username y password son obligatorios"}), 400

    user = User.query.filter_by(username=username).first()

    if user is None or not user.check_password(password):
        return jsonify({"error": "Credenciales inválidas"}), 401

    # La identidad del token es el id del usuario (como string, por
    # compatibilidad con flask-jwt-extended). El token expira solo,
    # según JWT_ACCESS_TOKEN_EXPIRES, sin que el servidor tenga que
    # guardar estado de sesión en memoria o base de datos.
    access_token = create_access_token(identity=str(user.id))

    return (
        jsonify(
            {
                "message": "Inicio de sesión exitoso",
                "access_token": access_token,
                "user": user.to_dict(),
            }
        ),
        200,
    )


# ---------------------------------------------------------------------------
# CRUD de tareas (protegido con JWT: header "Authorization: Bearer <token>")
# ---------------------------------------------------------------------------

@app.route("/tasks", methods=["GET"])
@jwt_required()
def get_tasks():
    user_id = get_current_user_id()
    tasks = Task.query.filter_by(user_id=user_id).all()
    return jsonify({"tasks": [t.to_dict() for t in tasks]}), 200


@app.route("/tasks/<int:task_id>", methods=["GET"])
@jwt_required()
def get_task(task_id):
    """
    Lee UNA sola tarea por su identificador.

    El filtro por user_id, además de localizar la tarea, garantiza que un
    usuario no pueda leer las tareas de otro: si el id existe pero pertenece
    a alguien más, la respuesta es 404 y no 403, para no revelar siquiera
    que ese registro existe.
    """
    user_id = get_current_user_id()
    task = Task.query.filter_by(id=task_id, user_id=user_id).first()

    if task is None:
        return jsonify({"error": "Tarea no encontrada"}), 404

    return jsonify({"task": task.to_dict()}), 200


@app.route("/tasks", methods=["POST"])
@jwt_required()
def create_task():
    user_id = get_current_user_id()
    data = get_json_body()

    title = data.get("title")
    if not title or not isinstance(title, str):
        return jsonify({"error": "El campo 'title' es obligatorio"}), 400

    description = data.get("description", "")
    status = data.get("status", "pending")

    new_task = Task(
        title=title,
        description=description if isinstance(description, str) else "",
        status=status if isinstance(status, str) else "pending",
        user_id=user_id,
    )

    db.session.add(new_task)
    db.session.commit()

    return jsonify({"message": "Tarea creada", "task": new_task.to_dict()}), 201


@app.route("/tasks/<int:task_id>", methods=["PUT"])
@jwt_required()
def update_task(task_id):
    user_id = get_current_user_id()
    # filter_by(id=..., user_id=...) es lo que garantiza que un usuario
    # solo pueda modificar SUS propias tareas.
    task = Task.query.filter_by(id=task_id, user_id=user_id).first()

    if task is None:
        return jsonify({"error": "Tarea no encontrada"}), 404

    data = get_json_body()

    if "title" in data:
        if not data["title"] or not isinstance(data["title"], str):
            return jsonify({"error": "'title' debe ser un texto no vacío"}), 400
        task.title = data["title"]

    if "description" in data and isinstance(data["description"], str):
        task.description = data["description"]

    if "status" in data and isinstance(data["status"], str):
        task.status = data["status"]

    db.session.commit()

    return jsonify({"message": "Tarea actualizada", "task": task.to_dict()}), 200


@app.route("/tasks/<int:task_id>", methods=["DELETE"])
@jwt_required()
def delete_task(task_id):
    user_id = get_current_user_id()
    task = Task.query.filter_by(id=task_id, user_id=user_id).first()

    if task is None:
        return jsonify({"error": "Tarea no encontrada"}), 404

    db.session.delete(task)
    db.session.commit()

    return jsonify({"message": "Tarea eliminada"}), 200


# ---------------------------------------------------------------------------
# Punto de entrada
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    debug_mode = (os.getenv("FLASK_DEBUG") or "false").lower() == "true"
    app.run(host="0.0.0.0", port=5000, debug=debug_mode)
