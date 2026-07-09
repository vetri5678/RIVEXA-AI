# ─── Stage 1: Base Python Image ───────────────────────────────────────────────
FROM python:3.11-slim AS base

WORKDIR /app

# System dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies separately for layer caching
COPY riskvision_ai_backend/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

# ─── Stage 2: Application Layer ───────────────────────────────────────────────
FROM base AS app

WORKDIR /app

# Copy backend application source
COPY riskvision_ai_backend/ ./

# Copy frontend static files to a /static subdirectory served by FastAPI
COPY index.html          ./static/index.html
COPY css/                ./static/css/
COPY js/                 ./static/js/
COPY assets/             ./static/assets/

# Create data directory for SQLite (dev mode)
RUN mkdir -p /app/data

# Runtime environment variables (override via docker-compose or k8s secrets)
ENV PYTHONPATH=/app
ENV PYTHONUNBUFFERED=1
ENV PORT=8000

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
