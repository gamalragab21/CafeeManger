#!/bin/bash
# =============================================================================
# Deploy script (runs ON the VPS during CI/CD)
# =============================================================================
# This script is executed by the GitHub Actions deploy workflow.
# It pulls the latest code, builds, and restarts the backend.
# =============================================================================

set -euo pipefail

APP_DIR="/opt/waselak"
REPO_DIR="$APP_DIR/repo"

echo "═══════════════════════════════════════════"
echo "  Deploying Waselak Backend..."
echo "═══════════════════════════════════════════"

cd "$APP_DIR"

# ── 1. Pull latest code ──────────────────────
if [ -d "$REPO_DIR" ]; then
    echo "📥 Pulling latest code..."
    cd "$REPO_DIR"
    git fetch origin main
    git reset --hard origin/main
else
    echo "📥 Cloning repository..."
    git clone --depth 1 --branch main https://github.com/gamalragab21/CafeeManger.git "$REPO_DIR"
    cd "$REPO_DIR"
fi

# ── 2. Copy .env.prod ───────────────────────
if [ -f "$APP_DIR/.env.prod" ]; then
    cp "$APP_DIR/.env.prod" "$REPO_DIR/backend/.env.prod"
    echo "✅ .env.prod copied"
else
    echo "❌ .env.prod not found at $APP_DIR/.env.prod"
    echo "   Create it first: cp backend/.env.prod.template $APP_DIR/.env.prod"
    exit 1
fi

# ── 3. Build and restart ────────────────────
echo "🐳 Building and restarting..."
cd "$REPO_DIR/backend"

# Build new image
docker compose -f docker-compose.prod.yml build --no-cache backend

# Stop old containers, start new ones
docker compose -f docker-compose.prod.yml up -d

# ── 4. Health check ─────────────────────────
echo "🏥 Waiting for health check..."
for i in $(seq 1 30); do
    if curl -sf http://localhost:8080/health > /dev/null 2>&1 || \
       curl -sf http://localhost:8080/api/v1/health > /dev/null 2>&1 || \
       [ "$(docker compose -f docker-compose.prod.yml ps --format '{{.Status}}' backend 2>/dev/null | grep -c 'Up')" -gt 0 ]; then
        echo "✅ Backend is healthy!"
        break
    fi
    echo "  Waiting... ($i/30)"
    sleep 2
done

# ── 5. Cleanup ──────────────────────────────
echo "🧹 Cleaning up old images..."
docker image prune -f 2>/dev/null || true

# ── 6. Show status ──────────────────────────
echo ""
echo "═══════════════════════════════════════════"
echo "  ✅ Deployment Complete!"
echo "═══════════════════════════════════════════"
docker compose -f docker-compose.prod.yml ps
echo ""
