#!/bin/bash
# ─────────────────────────────────────────────────────────
# Run backend in PRODUCTION mode
# Database: waselak_prod (separate from dev)
# Config: env/backend-release.env
# ─────────────────────────────────────────────────────────

set -e

cd "$(dirname "$0")/.."

# Check env file exists
ENV_FILE="env/backend-release.env"
if [ ! -f "$ENV_FILE" ]; then
    echo "❌ $ENV_FILE not found!"
    echo ""
    echo "Create it from the template:"
    echo "  cp env/backend-release.env.template env/backend-release.env"
    echo "  # Then edit with your production secrets"
    exit 1
fi

echo "┌─────────────────────────────────────────────"
echo "│ 🚀 Starting Waselak Backend (PRODUCTION)"
echo "│ Config: $ENV_FILE"
echo "└─────────────────────────────────────────────"

# Load env
set -a
source <(grep -v '^#' "$ENV_FILE" | grep -v '^$')
set +a

# Option 1: Run with Docker Compose (recommended for production)
if [ "$1" = "--docker" ]; then
    echo "Starting with Docker Compose (prod)..."
    cp "$ENV_FILE" backend/.env.prod
    docker compose -f backend/docker-compose.prod.yml up --build -d
    echo ""
    echo "✅ Backend is running in background"
    echo "   Logs: docker compose -f backend/docker-compose.prod.yml logs -f"
    echo "   Stop: docker compose -f backend/docker-compose.prod.yml down"
    exit 0
fi

# Option 2: Run directly with Gradle
echo "Starting with Gradle (production config)..."
echo ""

./gradlew :backend:run --args="-config=application-prod.conf"
