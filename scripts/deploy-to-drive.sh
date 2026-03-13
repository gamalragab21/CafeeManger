#!/usr/bin/env bash
# =============================================================================
# Waselak Local Deploy Script
# =============================================================================
# Builds all artifacts (Android APKs + Desktop packages) and uploads them
# to Google Drive following the same naming convention as CI/CD.
#
# Usage:
#   ./scripts/deploy-to-drive.sh debug          # Build & upload debug
#   ./scripts/deploy-to-drive.sh release        # Build & upload release
#   ./scripts/deploy-to-drive.sh all            # Build & upload both
#
# Required (set in gradle.properties or as env vars):
#   GOOGLE_DRIVE_CREDENTIALS_FILE  - Path to OAuth client JSON file
#   GOOGLE_DRIVE_FOLDER_ID         - Parent folder ID on Google Drive
#
# Naming: {app}-v{VERSION}-{DDMMYY}-{buildType}-{platform}.{ext}
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# ── Parse arguments ──────────────────────────────────────────────────────────

BUILD_TYPE="${1:-all}"
if [[ "$BUILD_TYPE" != "debug" && "$BUILD_TYPE" != "release" && "$BUILD_TYPE" != "all" ]]; then
    echo "Usage: $0 [debug|release|all]"
    exit 1
fi

# ── Read from gradle.properties if env vars not set ─────────────────────────

read_gradle_prop() {
    local key=$1
    grep "^${key}=" "$PROJECT_DIR/gradle.properties" 2>/dev/null | cut -d'=' -f2 | tr -d ' '
}

if [ -z "${GOOGLE_DRIVE_CREDENTIALS_FILE:-}" ]; then
    GOOGLE_DRIVE_CREDENTIALS_FILE=$(read_gradle_prop "GOOGLE_DRIVE_CREDENTIALS_FILE")
fi

if [ -z "${GOOGLE_DRIVE_FOLDER_ID:-}" ]; then
    GOOGLE_DRIVE_FOLDER_ID=$(read_gradle_prop "GOOGLE_DRIVE_FOLDER_ID")
fi

# ── Validate ────────────────────────────────────────────────────────────────

if [ -z "${GOOGLE_DRIVE_CREDENTIALS_FILE:-}" ]; then
    echo "ERROR: GOOGLE_DRIVE_CREDENTIALS_FILE is not set."
    echo "  Set it in gradle.properties or as an env var."
    echo "  Example: GOOGLE_DRIVE_CREDENTIALS_FILE=keystore/gdrive-oauth-client.json"
    exit 1
fi

if [ -z "${GOOGLE_DRIVE_FOLDER_ID:-}" ]; then
    echo "ERROR: GOOGLE_DRIVE_FOLDER_ID is not set."
    echo "  Set it in gradle.properties or as an env var."
    echo "  Example: GOOGLE_DRIVE_FOLDER_ID=1aBcDeFgHiJkLmNoPqRsTuVwXyZ"
    exit 1
fi

if [ ! -f "$GOOGLE_DRIVE_CREDENTIALS_FILE" ]; then
    echo "ERROR: Credentials file not found: $GOOGLE_DRIVE_CREDENTIALS_FILE"
    exit 1
fi

# ── Read version from gradle.properties ──────────────────────────────────────

cd "$PROJECT_DIR"

VERSION=$(grep 'APP_VERSION_NAME' gradle.properties | cut -d'=' -f2 | tr -d ' ')
DATE=$(date +'%d%m%y')

echo "============================================================"
echo "Waselak Deploy to Google Drive"
echo "============================================================"
echo "  Version:    v$VERSION"
echo "  Date:       $DATE"
echo "  Build type: $BUILD_TYPE"
echo "============================================================"

# ── Detect platform for desktop packages ─────────────────────────────────────

case "$(uname -s)" in
    Darwin*)  PLATFORM="macos";   EXT="dmg" ;;
    Linux*)   PLATFORM="linux";   EXT="deb" ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM="windows"; EXT="msi" ;;
    *)        PLATFORM="unknown"; EXT="" ;;
esac

echo "  Platform:   $PLATFORM ($EXT)"
echo ""

# ── App modules ──────────────────────────────────────────────────────────────

ANDROID_APPS=(app-manager app-cashier app-delivery app-kds app-admin)
DESKTOP_APPS=(app-manager app-cashier app-delivery app-kds)

# ── Build functions ──────────────────────────────────────────────────────────

build_artifacts() {
    local build_type=$1

    if [ "$build_type" = "debug" ]; then
        echo ">> Building all debug artifacts (APKs + Desktop)..."
        ./gradlew buildAllDebug
    else
        echo ">> Building all release artifacts (APKs + Desktop)..."
        ./gradlew buildAllRelease
    fi
}

# ── Collect and rename artifacts ─────────────────────────────────────────────

collect_artifacts() {
    local build_type=$1
    local out_dir="build/deploy/$build_type"
    rm -rf "$out_dir"
    mkdir -p "$out_dir"

    local count=0

    # Android APKs
    for module in "${ANDROID_APPS[@]}"; do
        local name="${module#app-}"
        local apk
        apk=$(find "$module/build/outputs/apk/$build_type" \
                    -name "*.apk" -type f 2>/dev/null | head -1)
        if [ -n "$apk" ]; then
            local target="${name}-v${VERSION}-${DATE}-${build_type}-android.apk"
            cp "$apk" "$out_dir/$target"
            echo "  OK: $target"
            count=$((count + 1))
        else
            echo "  SKIP: No $build_type APK for $module"
        fi
    done

    # Desktop packages (current OS only)
    if [ -n "$EXT" ]; then
        for module in "${DESKTOP_APPS[@]}"; do
            local name="${module#app-}"
            local pkg
            pkg=$(find "$module/build/compose/binaries" \
                       -name "*.$EXT" -type f 2>/dev/null | head -1)
            if [ -n "$pkg" ]; then
                local target="${name}-v${VERSION}-${DATE}-${build_type}-${PLATFORM}.${EXT}"
                cp "$pkg" "$out_dir/$target"
                echo "  OK: $target"
                count=$((count + 1))
            else
                echo "  SKIP: No .$EXT for $module"
            fi
        done
    fi

    echo ""
    echo "  Collected $count artifacts in $out_dir/"
    echo ""
}

# ── Upload to Google Drive ───────────────────────────────────────────────────

upload_to_drive() {
    local build_type=$1
    local artifacts_dir="build/deploy/$build_type"

    # Check there are files to upload
    local file_count
    file_count=$(find "$artifacts_dir" -type f 2>/dev/null | wc -l | tr -d ' ')
    if [ "$file_count" = "0" ]; then
        echo "WARNING: No artifacts to upload for $build_type"
        return
    fi

    echo ">> Uploading $build_type artifacts to Google Drive..."
    python3 "$SCRIPT_DIR/gdrive-upload.py" \
        --credentials "$GOOGLE_DRIVE_CREDENTIALS_FILE" \
        --parent-folder "$GOOGLE_DRIVE_FOLDER_ID" \
        --version "$VERSION" \
        --build-type "$build_type" \
        --artifacts-dir "$artifacts_dir"
}

# ── Main ─────────────────────────────────────────────────────────────────────

if [ "$BUILD_TYPE" = "debug" ] || [ "$BUILD_TYPE" = "all" ]; then
    build_artifacts debug
    collect_artifacts debug
    upload_to_drive debug
fi

if [ "$BUILD_TYPE" = "release" ] || [ "$BUILD_TYPE" = "all" ]; then
    build_artifacts release
    collect_artifacts release
    upload_to_drive release
fi

echo "============================================================"
echo "Deploy complete!"
echo "============================================================"
