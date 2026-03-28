#!/bin/bash
# ─────────────────────────────────────────────────────────
# Run backend in DEVELOPMENT mode
# Database: waselak_db (localhost:5432)
# Config: env/backend-debug.env
# ─────────────────────────────────────────────────────────

set -e

echo "┌─────────────────────────────────────────────"
echo "│ Starting Waselak Backend (DEVELOPMENT)"
echo "│ Database: waselak_db @ localhost:5432"
echo "│ Config: env/backend-debug.env"
echo "└─────────────────────────────────────────────"

cd "$(dirname "$0")/.."

# Load env from file
ENV_FILE="env/backend-debug.env"
if [ -f "$ENV_FILE" ]; then
    echo "Loading config from $ENV_FILE"
    set -a
    source <(grep -v '^#' "$ENV_FILE" | grep -v '^$')
    set +a
else
    echo "WARNING: $ENV_FILE not found, using defaults from application.conf"
fi

# Option 1: Run with Docker Compose (includes PostgreSQL)
if [ "$1" = "--docker" ]; then
    echo "Starting with Docker Compose (dev)..."
    docker compose -f backend/docker-compose.yml up --build
    exit 0
fi

# Option 2: Run directly with Gradle (assumes PostgreSQL is already running)
echo "Starting with Gradle..."
echo "Make sure PostgreSQL is running on localhost:5432"
echo ""

./gradlew :backend:run
