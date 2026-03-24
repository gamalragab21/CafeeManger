#!/usr/bin/env bash
# =============================================================================
# Waselak Local Deploy Script (Dropbox)
# =============================================================================
# Builds all artifacts (Android APKs + Desktop packages) and uploads them
# to Dropbox following the same naming convention as CI/CD.
#
# Usage:
#   ./scripts/deploy-to-dropbox.sh debug              # Build & upload all debug
#   ./scripts/deploy-to-dropbox.sh release             # Build & upload all release
#   ./scripts/deploy-to-dropbox.sh debug android       # Build & upload Android debug only
#   ./scripts/deploy-to-dropbox.sh debug desktop       # Build & upload Desktop debug only
#   ./scripts/deploy-to-dropbox.sh release android     # Build & upload Android release only
#   ./scripts/deploy-to-dropbox.sh release desktop     # Build & upload Desktop release only
#   ./scripts/deploy-to-dropbox.sh upload-only debug   # Upload without building
#   ./scripts/deploy-to-dropbox.sh upload-only release # Upload without building
#
# Required (set in gradle.properties or as env vars):
#   DROPBOX_ACCESS_TOKEN  - Dropbox API access token
#
# Naming: {app}-v{VERSION}-{DDMMYY}-{buildType}-{platform}.{ext}
# Dropbox path: /waselak-builds/v{VERSION}/{buildType}/{filename}
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# ── Parse arguments ──────────────────────────────────────────────────────────

BUILD_TYPE="${1:-all}"
TARGET="${2:-all}"  # android, desktop, or all

UPLOAD_ONLY=false
if [ "$BUILD_TYPE" = "upload-only" ]; then
    UPLOAD_ONLY=true
    BUILD_TYPE="${2:-debug}"
    TARGET="${3:-all}"
fi

if [[ "$BUILD_TYPE" != "debug" && "$BUILD_TYPE" != "release" && "$BUILD_TYPE" != "all" ]]; then
    echo "Usage: $0 [debug|release|all|upload-only] [android|desktop|all]"
    exit 1
fi

# ── Read from gradle.properties if env vars not set ─────────────────────────

read_gradle_prop() {
    local key=$1
    grep "^${key}=" "$PROJECT_DIR/gradle.properties" 2>/dev/null | cut -d'=' -f2 | tr -d ' '
}

if [ -z "${DROPBOX_ACCESS_TOKEN:-}" ]; then
    DROPBOX_ACCESS_TOKEN=$(read_gradle_prop "DROPBOX_ACCESS_TOKEN")
fi

# ── Validate ────────────────────────────────────────────────────────────────

if [ -z "${DROPBOX_ACCESS_TOKEN:-}" ]; then
    echo "ERROR: DROPBOX_ACCESS_TOKEN is not set."
    echo "  Set it in gradle.properties or as an env var."
    echo "  Get token from: https://www.dropbox.com/developers/apps"
    exit 1
fi

# ── Read version from gradle.properties ──────────────────────────────────────

cd "$PROJECT_DIR"

VERSION=$(grep 'APP_VERSION_NAME' gradle.properties | cut -d'=' -f2 | tr -d ' ')
DATE=$(date +'%d%m%y')

echo "============================================================"
echo "Waselak Deploy to Dropbox"
echo "============================================================"
echo "  Version:    v$VERSION"
echo "  Date:       $DATE"
echo "  Build type: $BUILD_TYPE"
echo "  Target:     $TARGET"
echo "  Upload only: $UPLOAD_ONLY"
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

    if [ "$TARGET" = "android" ]; then
        echo ">> Building Android ${build_type} APKs..."
        if [ "$build_type" = "debug" ]; then
            ./gradlew assembleAllDebug
        else
            ./gradlew assembleAllRelease
        fi
    elif [ "$TARGET" = "desktop" ]; then
        echo ">> Building Desktop ${build_type} packages..."
        if [ "$build_type" = "debug" ]; then
            ./gradlew $(printf ':%s:packageDistributionForCurrentOS ' ${DESKTOP_APPS[@]})
        else
            ./gradlew $(printf ':%s:packageReleaseDistributionForCurrentOS ' ${DESKTOP_APPS[@]})
        fi
    else
        echo ">> Building all ${build_type} artifacts (APKs + Desktop)..."
        if [ "$build_type" = "debug" ]; then
            ./gradlew buildAllDebug
        else
            ./gradlew buildAllRelease
        fi
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
    if [ "$TARGET" = "android" ] || [ "$TARGET" = "all" ]; then
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
    fi

    # Desktop packages (current OS only)
    if [ "$TARGET" = "desktop" ] || [ "$TARGET" = "all" ]; then
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
    fi

    echo ""
    echo "  Collected $count artifacts in $out_dir/"
    echo ""
}

# ── Upload to Dropbox ────────────────────────────────────────────────────────

upload_to_dropbox() {
    local build_type=$1
    local artifacts_dir="build/deploy/$build_type"

    # Check there are files to upload
    local file_count
    file_count=$(find "$artifacts_dir" -type f 2>/dev/null | wc -l | tr -d ' ')
    if [ "$file_count" = "0" ]; then
        echo "WARNING: No artifacts to upload for $build_type"
        return
    fi

    local dropbox_folder="/waselak-builds/v${VERSION}/${build_type}"

    echo ">> Creating Dropbox folder: $dropbox_folder"

    # Create folder (ignore if exists)
    curl -s -X POST https://api.dropboxapi.com/2/files/create_folder_v2 \
        -H "Authorization: Bearer $DROPBOX_ACCESS_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"path\": \"/waselak-builds/v${VERSION}\", \"autorename\": false}" > /dev/null 2>&1 || true

    curl -s -X POST https://api.dropboxapi.com/2/files/create_folder_v2 \
        -H "Authorization: Bearer $DROPBOX_ACCESS_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"path\": \"$dropbox_folder\", \"autorename\": false}" > /dev/null 2>&1 || true

    echo ">> Uploading $build_type artifacts to Dropbox..."

    for file in "$artifacts_dir"/*; do
        if [ -f "$file" ]; then
            local filename
            filename=$(basename "$file")
            local filesize
            filesize=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null)
            local mb=$((filesize / 1024 / 1024))

            echo "  Uploading: $filename (${mb} MB)..."

            if [ "$filesize" -gt 150000000 ]; then
                # Large file: use upload session
                local session_id
                session_id=$(curl -s -X POST https://content.dropboxapi.com/2/files/upload_session/start \
                    -H "Authorization: Bearer $DROPBOX_ACCESS_TOKEN" \
                    -H "Dropbox-API-Arg: {\"close\": false}" \
                    -H "Content-Type: application/octet-stream" \
                    --data-binary '' | python3 -c "import json,sys; print(json.load(sys.stdin)['session_id'])")

                local offset=0
                local chunk_size=104857600  # 100MB

                while [ "$offset" -lt "$filesize" ]; do
                    local remaining=$((filesize - offset))
                    local this_chunk=$chunk_size
                    if [ "$remaining" -lt "$this_chunk" ]; then
                        this_chunk=$remaining
                    fi

                    dd if="$file" bs=1 skip=$offset count=$this_chunk 2>/dev/null | \
                    curl -s -X POST https://content.dropboxapi.com/2/files/upload_session/append_v2 \
                        -H "Authorization: Bearer $DROPBOX_ACCESS_TOKEN" \
                        -H "Dropbox-API-Arg: {\"cursor\": {\"session_id\": \"$session_id\", \"offset\": $offset}, \"close\": false}" \
                        -H "Content-Type: application/octet-stream" \
                        --data-binary @- > /dev/null

                    offset=$((offset + this_chunk))
                    echo "    Progress: $((offset * 100 / filesize))%"
                done

                curl -s -X POST https://content.dropboxapi.com/2/files/upload_session/finish \
                    -H "Authorization: Bearer $DROPBOX_ACCESS_TOKEN" \
                    -H "Dropbox-API-Arg: {\"cursor\": {\"session_id\": \"$session_id\", \"offset\": $filesize}, \"commit\": {\"path\": \"$dropbox_folder/$filename\", \"mode\": \"overwrite\"}}" \
                    -H "Content-Type: application/octet-stream" \
                    --data-binary '' > /dev/null

                echo "  ✅ Done: $filename"
            else
                # Small file: simple upload
                local http_code
                http_code=$(curl -s -w "%{http_code}" -o /dev/null -X POST https://content.dropboxapi.com/2/files/upload \
                    -H "Authorization: Bearer $DROPBOX_ACCESS_TOKEN" \
                    -H "Dropbox-API-Arg: {\"path\": \"$dropbox_folder/$filename\", \"mode\": \"overwrite\"}" \
                    -H "Content-Type: application/octet-stream" \
                    --data-binary @"$file")

                if [ "$http_code" = "200" ]; then
                    echo "  ✅ Done: $filename"
                else
                    echo "  ❌ Failed ($http_code): $filename"
                fi
            fi
        fi
    done

    echo ""
}

# ── Main ─────────────────────────────────────────────────────────────────────

if [ "$BUILD_TYPE" = "debug" ] || [ "$BUILD_TYPE" = "all" ]; then
    if [ "$UPLOAD_ONLY" = false ]; then
        build_artifacts debug
    fi
    collect_artifacts debug
    upload_to_dropbox debug
fi

if [ "$BUILD_TYPE" = "release" ] || [ "$BUILD_TYPE" = "all" ]; then
    if [ "$UPLOAD_ONLY" = false ]; then
        build_artifacts release
    fi
    collect_artifacts release
    upload_to_dropbox release
fi

echo "============================================================"
echo "✅ Deploy complete! Files at: /waselak-builds/v${VERSION}/"
echo "============================================================"
