#!/bin/bash
# ─────────────────────────────────────────────────────────
# Run backend in DEVELOPMENT mode
# Database: waselak_db (localhost:5432)
# ─────────────────────────────────────────────────────────

set -e

echo "┌─────────────────────────────────────────────"
echo "│ Starting Waselak Backend (DEVELOPMENT)"
echo "│ Database: waselak_db @ localhost:5432"
echo "└─────────────────────────────────────────────"

cd "$(dirname "$0")"

# Option 1: Run with Docker Compose (includes PostgreSQL)
if [ "$1" = "--docker" ]; then
    echo "Starting with Docker Compose (dev)..."
    docker compose -f docker-compose.yml up --build
    exit 0
fi

# Option 2: Run directly with Gradle (assumes PostgreSQL is already running)
echo "Starting with Gradle..."
echo "Make sure PostgreSQL is running on localhost:5432"
echo ""

# Dev secrets — HMAC_SECRET must match env/debug.properties in mobile apps
export HMAC_SECRET="0003e100bafedf7a06d298c612cce6560bfd29dfad656d23303b7b0f05ac4ab2"
export JWT_SECRET="ec1e287139629ae6a79ea83377d045b777d4444e645bf9a78c5903f87cf0b28f"
export ADMIN_JWT_SECRET="c629b29f1ada461af9507f2ac8ecae3275263730acd28b8303d358d5b4b16ef7"
export ADMIN_NAME="Gamal Ragab"
export ADMIN_EMAIL="gamalragab217@gmail.com"
export ADMIN_PASSWORD="123456"

./gradlew run
