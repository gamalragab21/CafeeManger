#!/bin/bash
# ─────────────────────────────────────────────────────────
# Run backend in PRODUCTION mode
# Database: waselak_prod (separate from dev)
# ─────────────────────────────────────────────────────────

set -e

cd "$(dirname "$0")"

# Check .env.prod exists
if [ ! -f ".env.prod" ]; then
    echo "❌ .env.prod not found!"
    echo ""
    echo "Create it from the template:"
    echo "  cp .env.prod.template .env.prod"
    echo "  # Then edit .env.prod with your production secrets"
    exit 1
fi

echo "┌─────────────────────────────────────────────"
echo "│ 🚀 Starting Waselak Backend (PRODUCTION)"
echo "│ Database: waselak_prod"
echo "└─────────────────────────────────────────────"

# Option 1: Run with Docker Compose (recommended for production)
if [ "$1" = "--docker" ]; then
    echo "Starting with Docker Compose (prod)..."
    docker compose -f docker-compose.prod.yml up --build -d
    echo ""
    echo "✅ Backend is running in background"
    echo "   Logs: docker compose -f docker-compose.prod.yml logs -f"
    echo "   Stop: docker compose -f docker-compose.prod.yml down"
    exit 0
fi

# Option 2: Run directly with Gradle (uses .env.prod values)
echo "Starting with Gradle (production config)..."
echo ""

# Load .env.prod and export as environment variables
set -a
source .env.prod
set +a

# Override database connection to production
export DATABASE_URL="jdbc:postgresql://localhost:5432/${POSTGRES_DB:-waselak_prod}"
export DATABASE_USER="${POSTGRES_USER:-waselak_prod}"
export DATABASE_PASSWORD="${POSTGRES_PASSWORD}"

echo "DATABASE_URL=$DATABASE_URL"
echo ""

./gradlew run
