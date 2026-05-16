#!/usr/bin/env bash
# =============================================================================
# Waselak Backend Deploy to VPS
# =============================================================================
# Builds the backend fat JAR and deploys it to the VPS.
#
# Usage:
#   ./scripts/deploy-backend-vps.sh                # Build + deploy
#   ./scripts/deploy-backend-vps.sh --jar-only     # Build JAR only (no deploy)
#   ./scripts/deploy-backend-vps.sh --deploy-only  # Deploy existing JAR (no build)
#
# Required:
#   - SSH key at ~/.ssh/id_ed25519_hostinger
#   - VPS reachable at api.waselak.online / debug.waselak.online (HTTPS)
#     and 187.124.47.222 (raw SSH/SCP, and the deploy webhook on :9090)
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
# SCP/SSH still use the raw IP because the hostnames resolve to nginx on
# :443/:80 — they're for HTTP traffic, not the OpenSSH daemon. The
# webhook (:9090) is also unproxied so it stays on the IP too.
VPS_HOST="187.124.47.222"
VPS_USER="root"
VPS_SSH_KEY="$HOME/.ssh/id_ed25519_hostinger"
VPS_JAR_PATH="/opt/waselak/waselak-backend.jar"
VPS_DEPLOY_TOKEN="waselak-deploy-2026-secret"
VPS_WEBHOOK_PORT="9090"
# Public health-check hostnames (TLS, behind nginx).
RELEASE_URL="https://api.waselak.online"
DEBUG_URL="https://debug.waselak.online"

JAR_PATH="$PROJECT_DIR/backend/build/libs/backend-all.jar"
LOCAL_JAR="$PROJECT_DIR/build/deploy/waselak-backend.jar"

MODE="${1:-full}"  # full, --jar-only, --deploy-only

cd "$PROJECT_DIR"

VERSION=$(grep 'APP_VERSION_NAME' gradle.properties | cut -d'=' -f2 | tr -d ' ')

echo "============================================================"
echo "Waselak Backend Deploy to VPS"
echo "============================================================"
echo "  Version:  v$VERSION"
echo "  VPS:      $VPS_USER@$VPS_HOST"
echo "  Mode:     $MODE"
echo "============================================================"

# ── Build ────────────────────────────────────────────────────────────────────

if [ "$MODE" != "--deploy-only" ]; then
    echo ""
    echo ">> Building backend fat JAR..."
    ./gradlew :backend:buildFatJar --quiet

    mkdir -p build/deploy
    cp "$JAR_PATH" "$LOCAL_JAR"
    JAR_SIZE=$(stat -f%z "$LOCAL_JAR" 2>/dev/null || stat -c%s "$LOCAL_JAR" 2>/dev/null)
    echo "✅ JAR built: $(( JAR_SIZE / 1024 / 1024 )) MB"
fi

if [ "$MODE" = "--jar-only" ]; then
    echo ""
    echo "✅ JAR ready at: $LOCAL_JAR"
    exit 0
fi

# ── Deploy ───────────────────────────────────────────────────────────────────

echo ""
echo ">> Uploading JAR to VPS..."

# Try SCP first (direct file transfer)
if scp -i "$VPS_SSH_KEY" -o StrictHostKeyChecking=no -o ConnectTimeout=10 \
     "$LOCAL_JAR" "$VPS_USER@$VPS_HOST:$VPS_JAR_PATH" 2>/dev/null; then
    echo "✅ JAR uploaded via SCP"

    # Restart services
    echo ">> Restarting VPS services..."
    ssh -i "$VPS_SSH_KEY" -o StrictHostKeyChecking=no -o ConnectTimeout=10 \
        "$VPS_USER@$VPS_HOST" \
        "echo 'v$VERSION' > /opt/waselak/CURRENT_VERSION && \
         echo \"\$(date '+%Y-%m-%d %H:%M:%S') | DEPLOY SUCCESS | v$VERSION | local gradle deploy\" >> /opt/waselak/history/deploys.log && \
         systemctl restart waselak-release waselak-debug" 2>&1

    echo ">> Waiting for services to start..."
    sleep 15
else
    echo "⚠️ SCP failed, trying webhook..."

    # Fallback: use webhook (if SCP/SSH not available)
    RESULT=$(curl -s -m 120 "http://$VPS_HOST:$VPS_WEBHOOK_PORT/deploy?tag=v$VERSION&token=$VPS_DEPLOY_TOKEN" 2>&1)
    echo "Webhook response: $RESULT"

    if echo "$RESULT" | grep -q "success"; then
        echo "✅ Deployed via webhook"
    else
        echo "❌ Deploy failed. Check VPS manually."
        exit 1
    fi
fi

# ── Health Check ─────────────────────────────────────────────────────────────

echo ""
echo ">> Health check..."
RELEASE=$(curl -s -m 5 "$RELEASE_URL/health" 2>/dev/null || echo "❌ Not reachable")
DEBUG=$(curl -s -m 5 "$DEBUG_URL/health" 2>/dev/null || echo "❌ Not reachable")

echo "Release: $RELEASE_URL → $RELEASE"
echo "Debug:   $DEBUG_URL → $DEBUG"

echo ""
echo "============================================================"
echo "✅ Backend v$VERSION deployed to VPS!"
echo "============================================================"
