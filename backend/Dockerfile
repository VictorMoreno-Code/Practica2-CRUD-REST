FROM python:3.11-slim

# Evita que Python genere archivos .pyc y fuerza la salida sin buffer,
# para ver los logs en tiempo real dentro del contenedor.
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

# Se copian primero los requirements para aprovechar el cache de capas de
# Docker: si solo cambia el código, no se vuelven a instalar los paquetes.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 5000

CMD ["python", "app.py"]
