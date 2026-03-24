#!/bin/bash
# =============================================================================
# Download artifacts from GitHub Actions and upload to Google Drive
# =============================================================================
# Usage:
#   ./scripts/download-and-deploy.sh [run_id]
#
# If run_id is not provided, uses the latest successful run.
# Requires: gh CLI (authenticated), python3
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPLOY_DIR="$ROOT_DIR/build/deploy-from-ci"

# Read version from gradle.properties
VERSION=$(grep 'APP_VERSION_NAME' "$ROOT_DIR/gradle.properties" | cut -d'=' -f2 | tr -d ' ')
DATE=$(date +'%d%m%y')

echo "═══════════════════════════════════════════"
echo "  Download & Deploy v${VERSION}"
echo "═══════════════════════════════════════════"

# ── Step 1: Check gh CLI ─────────────────────
if ! command -v gh &> /dev/null; then
    echo "❌ gh CLI not found. Install: brew install gh"
    exit 1
fi

if ! gh auth status &> /dev/null; then
    echo "❌ gh CLI not authenticated. Run: gh auth login"
    exit 1
fi

echo "✅ gh CLI authenticated"

# ── Step 2: Find the CI run ──────────────────
RUN_ID="${1:-}"

if [ -z "$RUN_ID" ]; then
    echo "🔍 Finding latest successful CI run for v${VERSION}..."
    RUN_ID=$(gh run list --workflow="ci.yml" --status=success --limit=5 --json databaseId,headBranch,displayTitle \
        | python3 -c "
import json, sys
runs = json.load(sys.stdin)
for r in runs:
    if 'v${VERSION}' in r.get('displayTitle', '') or r.get('headBranch') == 'main':
        print(r['databaseId'])
        sys.exit(0)
if runs:
    print(runs[0]['databaseId'])
" 2>/dev/null || echo "")

    if [ -z "$RUN_ID" ]; then
        echo "❌ No successful CI run found. Provide run ID manually:"
        echo "   ./scripts/download-and-deploy.sh <RUN_ID>"
        echo ""
        echo "Recent runs:"
        gh run list --workflow="ci.yml" --limit=5
        exit 1
    fi
fi

echo "📦 Using CI run: $RUN_ID"

# ── Step 3: Download artifacts ───────────────
rm -rf "$DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR/debug" "$DEPLOY_DIR/release"

echo ""
echo "⬇️  Downloading artifacts from run $RUN_ID..."

# Download each artifact type
for artifact_name in android-debug android-release desktop-release-linux desktop-release-macos desktop-release-windows; do
    echo "  📥 $artifact_name..."
    if gh run download "$RUN_ID" --name "$artifact_name" --dir "$DEPLOY_DIR/tmp-$artifact_name" 2>/dev/null; then
        # Move files to correct folder
        if [[ "$artifact_name" == *"debug"* ]]; then
            mv "$DEPLOY_DIR/tmp-$artifact_name"/* "$DEPLOY_DIR/debug/" 2>/dev/null || true
        else
            mv "$DEPLOY_DIR/tmp-$artifact_name"/* "$DEPLOY_DIR/release/" 2>/dev/null || true
        fi
        rm -rf "$DEPLOY_DIR/tmp-$artifact_name"
        echo "     ✅ Downloaded"
    else
        echo "     ⚠️  Not found (skipped)"
    fi
done

echo ""
echo "📂 Downloaded artifacts:"
echo "── Debug ──"
ls -la "$DEPLOY_DIR/debug/" 2>/dev/null || echo "  (none)"
echo "── Release ──"
ls -la "$DEPLOY_DIR/release/" 2>/dev/null || echo "  (none)"

# Count files
DEBUG_COUNT=$(find "$DEPLOY_DIR/debug" -type f 2>/dev/null | wc -l | tr -d ' ')
RELEASE_COUNT=$(find "$DEPLOY_DIR/release" -type f 2>/dev/null | wc -l | tr -d ' ')
TOTAL=$((DEBUG_COUNT + RELEASE_COUNT))

if [ "$TOTAL" -eq 0 ]; then
    echo "❌ No artifacts downloaded. Check the run ID."
    exit 1
fi

echo ""
echo "📊 Total: $DEBUG_COUNT debug + $RELEASE_COUNT release = $TOTAL files"

# ── Step 4: Upload to Google Drive ───────────
echo ""
echo "☁️  Uploading to Google Drive..."

CREDENTIALS="$ROOT_DIR/keystore/gdrive-oauth-client.json"
PARENT_FOLDER_FILE="$ROOT_DIR/keystore/gdrive-folder-id.txt"

if [ ! -f "$CREDENTIALS" ]; then
    echo "❌ Google Drive credentials not found at: $CREDENTIALS"
    echo "   Place your OAuth client JSON there."
    exit 1
fi

PARENT_FOLDER=""
if [ -f "$PARENT_FOLDER_FILE" ]; then
    PARENT_FOLDER=$(cat "$PARENT_FOLDER_FILE" | tr -d '[:space:]')
fi

if [ -z "$PARENT_FOLDER" ]; then
    echo "❌ Google Drive folder ID not found."
    echo "   Create keystore/gdrive-folder-id.txt with your folder ID."
    exit 1
fi

# Upload debug
if [ "$DEBUG_COUNT" -gt 0 ]; then
    echo "  📤 Uploading debug artifacts..."
    python3 "$SCRIPT_DIR/gdrive-upload.py" \
        --credentials "$CREDENTIALS" \
        --parent-folder "$PARENT_FOLDER" \
        --version "$VERSION" \
        --build-type debug \
        --artifacts-dir "$DEPLOY_DIR/debug"
fi

# Upload release
if [ "$RELEASE_COUNT" -gt 0 ]; then
    echo "  📤 Uploading release artifacts..."
    python3 "$SCRIPT_DIR/gdrive-upload.py" \
        --credentials "$CREDENTIALS" \
        --parent-folder "$PARENT_FOLDER" \
        --version "$VERSION" \
        --build-type release \
        --artifacts-dir "$DEPLOY_DIR/release"
fi

echo ""
echo "═══════════════════════════════════════════"
echo "  ✅ Done! v${VERSION} deployed to Google Drive"
echo "  📦 $TOTAL artifacts uploaded"
echo "═══════════════════════════════════════════"
